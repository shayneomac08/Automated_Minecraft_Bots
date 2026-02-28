package com.shayneomac08.automated_minecraft_bots.agent;

import java.util.List;

public record ActionPlan(List<Action> actions) {

    public record Action(
            String type,
            String text,
            Double x,
            Double y,
            Double z,
            Double speed,
            Integer seconds,

            // NEW: used by set_goal
            String goal,
            Double minutes
    ) {}
}
