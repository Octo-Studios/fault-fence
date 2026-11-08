package it.hurts.shatterbyte.faultfence.common.report;

import it.hurts.shatterbyte.faultfence.common.config.RecoveryCategory;

public record ResourceOrigin(LoadReport report, RecoveryCategory category, String type, String id, String file, String pack) {
    public String node() {
        return type + " " + id;
    }
}
