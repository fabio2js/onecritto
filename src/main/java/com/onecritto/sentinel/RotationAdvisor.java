package com.onecritto.sentinel;

import com.onecritto.i18n.I18n;
import com.onecritto.sentinel.model.PasswordScore;
import com.onecritto.sentinel.model.SentinelReport;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Produces a prioritized password rotation plan based on risk level and age.
 *
 * Priority order:
 *   1. CRITICAL passwords (common, very weak)
 *   2. WEAK passwords
 *   3. Duplicate passwords
 *   4. Old passwords (> 90 days)
 *   5. FAIR passwords
 */
public class RotationAdvisor {

    private static final int OLD_PASSWORD_DAYS = 90;

    /**
     * Returns a prioritized list of entries that should be rotated,
     * most urgent first.
     */
    public List<RotationItem> buildPlan(List<SentinelReport> reports) {
        List<RotationItem> plan = new ArrayList<>();

        for (SentinelReport r : reports) {
            PasswordScore ps = r.getPasswordScore();
            if (ps == null) continue;

            int priority = computePriority(ps, r.getPasswordAgeDays());
            if (priority > 0) {
                plan.add(new RotationItem(
                        r.getEntryId(),
                        r.getEntryTitle(),
                        priority,
                        buildReason(ps, r.getPasswordAgeDays())
                ));
            }
        }

        // sort: highest priority first (lower number = more urgent)
        plan.sort(Comparator.comparingInt(RotationItem::priority));
        return plan;
    }

    private int computePriority(PasswordScore ps, int ageDays) {
        PasswordScore.RiskLevel level = ps.getRiskLevel();
        if (ps.isCommonPassword() || ps.isHasLeetSpeak() || level == PasswordScore.RiskLevel.CRITICAL) return 1;
        if (level == PasswordScore.RiskLevel.WEAK)   return 2;
        if (ps.isDuplicate())                         return 3;
        if (ageDays > OLD_PASSWORD_DAYS)              return 4;
        if (level == PasswordScore.RiskLevel.FAIR)    return 5;
        return 0; // no rotation needed
    }

    private String buildReason(PasswordScore ps, int ageDays) {
        List<String> reasons = new ArrayList<>();
        if (ps.isCommonPassword() || ps.isHasLeetSpeak()) reasons.add(I18n.t("sentinel.rotation.common"));
        if (ps.getRiskLevel() == PasswordScore.RiskLevel.CRITICAL) reasons.add(I18n.t("sentinel.rotation.critical"));
        if (ps.getRiskLevel() == PasswordScore.RiskLevel.WEAK) reasons.add(I18n.t("sentinel.rotation.weak"));
        if (ps.isDuplicate()) reasons.add(I18n.t("sentinel.rotation.duplicate"));
        if (ageDays > OLD_PASSWORD_DAYS) reasons.add(I18n.t("sentinel.rotation.old"));
        if (ps.isHasKeyboardPattern()) reasons.add(I18n.t("sentinel.rotation.pattern"));
        return reasons.isEmpty() ? I18n.t("sentinel.rotation.improve") : String.join(", ", reasons);
    }

    /** A single rotation recommendation. */
    public record RotationItem(String entryId, String entryTitle, int priority, String reason) {}
}
