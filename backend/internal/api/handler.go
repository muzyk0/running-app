package api

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/muzyk0/running-app/backend/internal/provider"
	"github.com/muzyk0/running-app/backend/internal/service"
)

const maxRequestBodyBytes = 1 << 20

type trainingService interface {
	GenerateTrainingStream(
		ctx context.Context,
		request provider.GenerateRequest,
		report provider.ProgressReporter,
	) (provider.TrainingEnvelope, error)
}

type handler struct {
	logger         *slog.Logger
	service        trainingService
	requestTimeout time.Duration
}

type generateTrainingRequest struct {
	Profile profileRequest `json:"profile"`
	Request requestContext `json:"request"`
}

type profileRequest struct {
	HeightCM               int                  `json:"height_cm"`
	WeightKG               int                  `json:"weight_kg"`
	Sex                    string               `json:"sex"`
	Age                    int                  `json:"age"`
	TrainingDaysPerWeek    int                  `json:"training_days_per_week"`
	FitnessLevel           string               `json:"fitness_level"`
	InjuriesAndLimitations string               `json:"injuries_and_limitations"`
	TrainingGoal           string               `json:"training_goal"`
	AdditionalPromptFields []promptFieldRequest `json:"additional_prompt_fields,omitempty"`
}

type promptFieldRequest struct {
	Label string `json:"label"`
	Value string `json:"value"`
}

type requestContext struct {
	UserNote string `json:"user_note,omitempty"`
	Locale   string `json:"locale"`
}

type healthResponse struct {
	Status string `json:"status"`
}

type errorEnvelope struct {
	Error apiError `json:"error"`
}

type apiError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

func NewRouter(logger *slog.Logger, trainingService trainingService, requestTimeout time.Duration) http.Handler {
	if logger == nil {
		logger = slog.Default()
	}

	h := &handler{
		logger:         logger,
		service:        trainingService,
		requestTimeout: requestTimeout,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", h.handleHealthz)
	mux.HandleFunc("POST /v1/trainings/generate", h.handleGenerateTraining)

	return h.withRequestLogging(mux)
}

func (h *handler) handleHealthz(w http.ResponseWriter, _ *http.Request) {
	writeJSON(w, http.StatusOK, healthResponse{Status: "ok"})
}

func (h *handler) handleGenerateTraining(w http.ResponseWriter, r *http.Request) {
	if h.service == nil {
		writeError(w, http.StatusServiceUnavailable, "service_unavailable", "training service is not configured")
		return
	}

	var payload generateTrainingRequest
	if err := decodeJSONBody(w, r, &payload); err != nil {
		writeError(w, http.StatusBadRequest, "invalid_json", err.Error())
		return
	}

	generateRequest, err := payload.toProviderRequest()
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid_request", err.Error())
		return
	}

	ctx, cancel := context.WithTimeout(r.Context(), h.requestTimeout)
	defer cancel()

	flusher, ok := w.(http.Flusher)
	if !ok {
		writeError(w, http.StatusInternalServerError, "streaming_unsupported", "response writer does not support streaming")
		return
	}

	startEventStream(w)
	flusher.Flush()

	var streamWriteErr error
	response, err := h.service.GenerateTrainingStream(ctx, generateRequest, func(chunk provider.ProgressChunk) {
		if streamWriteErr != nil {
			return
		}
		if err := writeSSEEvent(w, flusher, provider.StreamEventLog, chunk); err != nil {
			streamWriteErr = err
		}
	})
	if err != nil {
		if writeErr := writeSSEEvent(w, flusher, provider.StreamEventError, errorEnvelope{
			Error: mapGenerationError(err),
		}); writeErr != nil {
			h.logger.Error("write stream error event failed", "error", writeErr)
		}
		return
	}

	if streamWriteErr != nil {
		h.logger.Error("write stream log event failed", "error", streamWriteErr)
		return
	}

	if err := writeSSEEvent(w, flusher, provider.StreamEventCompleted, response); err != nil {
		h.logger.Error("write stream completed event failed", "error", err)
	}
}

func (r generateTrainingRequest) toProviderRequest() (provider.GenerateRequest, error) {
	if r.Profile.HeightCM <= 0 {
		return provider.GenerateRequest{}, errors.New("profile.height_cm must be greater than zero")
	}
	if r.Profile.WeightKG <= 0 {
		return provider.GenerateRequest{}, errors.New("profile.weight_kg must be greater than zero")
	}
	if strings.TrimSpace(r.Profile.Sex) == "" {
		return provider.GenerateRequest{}, errors.New("profile.sex is required")
	}
	if r.Profile.Age <= 0 {
		return provider.GenerateRequest{}, errors.New("profile.age must be greater than zero")
	}
	if r.Profile.TrainingDaysPerWeek <= 0 {
		return provider.GenerateRequest{}, errors.New("profile.training_days_per_week must be greater than zero")
	}
	if strings.TrimSpace(r.Profile.FitnessLevel) == "" {
		return provider.GenerateRequest{}, errors.New("profile.fitness_level is required")
	}
	if strings.TrimSpace(r.Profile.InjuriesAndLimitations) == "" {
		return provider.GenerateRequest{}, errors.New("profile.injuries_and_limitations is required")
	}
	if strings.TrimSpace(r.Profile.TrainingGoal) == "" {
		return provider.GenerateRequest{}, errors.New("profile.training_goal is required")
	}
	if strings.TrimSpace(r.Request.Locale) != "ru-RU" {
		return provider.GenerateRequest{}, errors.New("request.locale must be ru-RU for MVP")
	}

	additionalFields := make([]provider.AdditionalPromptField, 0, len(r.Profile.AdditionalPromptFields))
	for _, field := range r.Profile.AdditionalPromptFields {
		label := strings.TrimSpace(field.Label)
		value := strings.TrimSpace(field.Value)
		if label == "" || value == "" {
			return provider.GenerateRequest{}, errors.New("profile.additional_prompt_fields entries must include non-empty label and value")
		}
		additionalFields = append(additionalFields, provider.AdditionalPromptField{
			Label: label,
			Value: value,
		})
	}

	return provider.GenerateRequest{
		Profile: provider.ProfileSnapshot{
			HeightCM:               r.Profile.HeightCM,
			WeightKG:               r.Profile.WeightKG,
			Sex:                    strings.TrimSpace(r.Profile.Sex),
			Age:                    r.Profile.Age,
			TrainingDaysPerWeek:    r.Profile.TrainingDaysPerWeek,
			FitnessLevel:           strings.TrimSpace(r.Profile.FitnessLevel),
			InjuriesAndLimitations: strings.TrimSpace(r.Profile.InjuriesAndLimitations),
			TrainingGoal:           strings.TrimSpace(r.Profile.TrainingGoal),
			AdditionalPromptFields: additionalFields,
		},
		Locale:   "ru-RU",
		UserNote: strings.TrimSpace(r.Request.UserNote),
	}, nil
}

func decodeJSONBody(w http.ResponseWriter, r *http.Request, target any) error {
	r.Body = http.MaxBytesReader(w, r.Body, maxRequestBodyBytes)
	defer r.Body.Close()

	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()

	if err := decoder.Decode(target); err != nil {
		return err
	}

	if err := decoder.Decode(&struct{}{}); err != nil {
		if errors.Is(err, io.EOF) {
			return nil
		}
		return errors.New("request body must contain exactly one JSON object")
	}

	return errors.New("request body must contain exactly one JSON object")
}

func writeJSON(w http.ResponseWriter, status int, payload any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(payload)
}

func writeError(w http.ResponseWriter, status int, code string, message string) {
	writeJSON(w, status, errorEnvelope{
		Error: apiError{
			Code:    code,
			Message: message,
		},
	})
}

func mapGenerationError(err error) apiError {
	switch {
	case errors.Is(err, context.DeadlineExceeded):
		return apiError{Code: "request_timeout", Message: "generation timed out"}
	case errors.Is(err, service.ErrProviderNotConfigured):
		return apiError{Code: "service_unavailable", Message: "training provider is not configured"}
	default:
		return apiError{Code: "provider_error", Message: "training generation failed"}
	}
}

func startEventStream(w http.ResponseWriter) {
	headers := w.Header()
	headers.Set("Content-Type", "text/event-stream; charset=utf-8")
	headers.Set("Cache-Control", "no-cache")
	headers.Set("X-Accel-Buffering", "no")
	w.WriteHeader(http.StatusOK)
}

func writeSSEEvent(w io.Writer, flusher http.Flusher, eventType provider.StreamEventType, payload any) error {
	data, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	if _, err := io.WriteString(w, "event: "+string(eventType)+"\n"); err != nil {
		return err
	}
	if _, err := io.WriteString(w, "data: "+string(data)+"\n\n"); err != nil {
		return err
	}

	flusher.Flush()
	return nil
}

func (h *handler) withRequestLogging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		startedAt := time.Now()
		recorder := &statusRecorder{ResponseWriter: w, statusCode: http.StatusOK}

		next.ServeHTTP(recorder, r)

		h.logger.Info(
			"http request completed",
			"method", r.Method,
			"path", r.URL.Path,
			"status", recorder.statusCode,
			"duration_ms", time.Since(startedAt).Milliseconds(),
		)
	})
}

type statusRecorder struct {
	http.ResponseWriter
	statusCode int
}

func (r *statusRecorder) WriteHeader(statusCode int) {
	r.statusCode = statusCode
	r.ResponseWriter.WriteHeader(statusCode)
}

func (r *statusRecorder) Flush() {
	flusher, ok := r.ResponseWriter.(http.Flusher)
	if !ok {
		return
	}

	flusher.Flush()
}
