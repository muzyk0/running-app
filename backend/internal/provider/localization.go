package provider

import "strings"

const (
	SupportedLocaleRussian = "ru-RU"
	SupportedLocaleEnglish = "en-US"
	DefaultLocale          = SupportedLocaleRussian

	DefaultDisclaimerEnglish = "This app does not provide medical advice."
)

var supportedLocales = []string{SupportedLocaleRussian, SupportedLocaleEnglish}

func SupportedLocales() []string {
	locales := make([]string, len(supportedLocales))
	copy(locales, supportedLocales)
	return locales
}

func NormalizeSupportedLocale(locale string) (string, bool) {
	switch strings.TrimSpace(locale) {
	case SupportedLocaleRussian:
		return SupportedLocaleRussian, true
	case SupportedLocaleEnglish:
		return SupportedLocaleEnglish, true
	default:
		return "", false
	}
}

func EffectiveLocale(locale string) string {
	if normalized, ok := NormalizeSupportedLocale(locale); ok {
		return normalized
	}

	return DefaultLocale
}

func DisclaimerForLocale(locale string) string {
	switch EffectiveLocale(locale) {
	case SupportedLocaleEnglish:
		return DefaultDisclaimerEnglish
	default:
		return DefaultDisclaimer
	}
}
