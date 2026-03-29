package com.onecritto.sentinel;

import com.onecritto.i18n.I18n;
import com.onecritto.sentinel.model.PasswordScore;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Password Coach — generates actionable coaching tips for a single vault entry
 * based on Sentinel analysis data. Pure Java, zero AI.
 */
public class PasswordCoach {

    public enum Severity { CRITICAL, WARNING, INFO, OK }

    public record CoachTip(Severity severity, String icon, String message) {}

    /**
     * Generate coaching tips for a password entry.
     *
     * @param score          the PasswordScore from Sentinel analysis
     * @param passwordAgeDays how many days since the password was last changed (0 = unknown)
     * @return ordered list of tips, most critical first
     */
    public List<CoachTip> coach(PasswordScore score, int passwordAgeDays) {
        List<CoachTip> tips = new ArrayList<>();

        if (score == null) return tips;

        char[] emptyCheck = {};
        if (score.getScore() == 0 && score.getRiskLevel() == PasswordScore.RiskLevel.CRITICAL) {
            tips.add(new CoachTip(Severity.CRITICAL, "\u26A0",
                    I18n.t("coach.tip.no_password")));
            return tips;
        }

        // --- Common password ---
        if (score.isCommonPassword() || score.isHasLeetSpeak()) {
            tips.add(new CoachTip(Severity.CRITICAL, "\uD83D\uDEA8",
                    I18n.t("coach.tip.common")));
        }

        // --- Duplicate ---
        if (score.isDuplicate()) {
            tips.add(new CoachTip(Severity.CRITICAL, "\uD83D\uDD04",
                    I18n.t("coach.tip.duplicate")));
        }

        // --- Breach ---
        if (score.getBreachCount() != null && score.getBreachCount() > 0) {
            tips.add(new CoachTip(Severity.CRITICAL, "\uD83D\uDED1",
                    MessageFormat.format(I18n.t("coach.tip.breached"), score.getBreachCount())));
        }

        // --- Keyboard pattern ---
        if (score.isHasKeyboardPattern()) {
            tips.add(new CoachTip(Severity.WARNING, "\u2328",
                    I18n.t("coach.tip.keyboard_pattern")));
        }

        // --- Repetitions ---
        if (score.isHasRepetitions()) {
            tips.add(new CoachTip(Severity.WARNING, "\uD83D\uDD01",
                    I18n.t("coach.tip.repetitions")));
        }

        // --- Crack time estimation ---
        String crackTime = estimateCrackTime(score.getEntropyBits());
        tips.add(new CoachTip(
                score.getEntropyBits() < 40 ? Severity.CRITICAL :
                        score.getEntropyBits() < 60 ? Severity.WARNING : Severity.INFO,
                "\u23F1",
                MessageFormat.format(I18n.t("coach.tip.crack_time"),
                        String.format("%.0f", score.getEntropyBits()), crackTime)));

        // --- Entropy / length advice ---
        if (score.getEntropyBits() < 50) {
            tips.add(new CoachTip(Severity.WARNING, "\uD83D\uDCCF",
                    I18n.t("coach.tip.low_entropy")));
        }

        // --- Password age ---
        if (passwordAgeDays > 365) {
            tips.add(new CoachTip(Severity.WARNING, "\uD83D\uDCC5",
                    MessageFormat.format(I18n.t("coach.tip.very_old"), passwordAgeDays)));
        } else if (passwordAgeDays > 90) {
            tips.add(new CoachTip(Severity.INFO, "\uD83D\uDCC5",
                    MessageFormat.format(I18n.t("coach.tip.old"), passwordAgeDays)));
        }

        // --- Overall verdict ---
        if (score.getScore() >= 80) {
            tips.add(new CoachTip(Severity.OK, "\u2705",
                    I18n.t("coach.tip.strong")));
        } else if (score.getScore() >= 60) {
            tips.add(new CoachTip(Severity.INFO, "\uD83D\uDCA1",
                    I18n.t("coach.tip.could_be_better")));
        }

        return tips;
    }

    /**
     * Estimate offline crack time at 10 billion guesses/sec (modern GPU cluster).
     * Returns a human-readable string.
     */
    static String estimateCrackTime(double entropyBits) {
        if (entropyBits <= 0) return I18n.t("coach.crack.instant");

        // keyspace = 2^entropy, time = keyspace / guesses_per_sec / 2 (average)
        double logSeconds = (entropyBits - 1) * Math.log(2) - Math.log(10_000_000_000.0);

        if (logSeconds < 0) return I18n.t("coach.crack.instant");

        double seconds = Math.exp(logSeconds);

        if (seconds < 60)              return MessageFormat.format(I18n.t("coach.crack.seconds"), (long) seconds);
        if (seconds < 3600)            return MessageFormat.format(I18n.t("coach.crack.minutes"), (long) (seconds / 60));
        if (seconds < 86400)           return MessageFormat.format(I18n.t("coach.crack.hours"),   (long) (seconds / 3600));
        if (seconds < 86400.0 * 365)   return MessageFormat.format(I18n.t("coach.crack.days"),    (long) (seconds / 86400));
        if (seconds < 86400.0 * 365 * 1000)
            return MessageFormat.format(I18n.t("coach.crack.years"), (long) (seconds / (86400.0 * 365)));
        if (seconds < 86400.0 * 365 * 1_000_000)
            return MessageFormat.format(I18n.t("coach.crack.thousands_years"), (long) (seconds / (86400.0 * 365 * 1000)));

        return I18n.t("coach.crack.centuries");
    }
}
