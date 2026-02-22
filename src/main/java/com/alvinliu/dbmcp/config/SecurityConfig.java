package com.alvinliu.dbmcp.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Review config: whole_text_match / command_match / always_review_ddl. No defaults; omit to disable.
 */
public class SecurityConfig {
    private List<String> wholeTextMatch = new ArrayList<>();
    private List<String> commandMatch = new ArrayList<>();
    private boolean alwaysReviewDdl = false;

    public List<String> getWholeTextMatch() { return wholeTextMatch; }
    public void setWholeTextMatch(List<String> wholeTextMatch) {
        this.wholeTextMatch = wholeTextMatch != null ? wholeTextMatch : new ArrayList<>();
    }

    public List<String> getCommandMatch() { return commandMatch; }
    public void setCommandMatch(List<String> commandMatch) {
        this.commandMatch = commandMatch != null ? commandMatch : new ArrayList<>();
    }

    public boolean isAlwaysReviewDdl() { return alwaysReviewDdl; }
    public void setAlwaysReviewDdl(boolean alwaysReviewDdl) { this.alwaysReviewDdl = alwaysReviewDdl; }
}
