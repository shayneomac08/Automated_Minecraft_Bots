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

            // set_goal fields
            String goal,
            Double minutes,

            // plan_queue fields: ordered list of tasks + durations
            List<String> queuedTasks,
            List<Double> queuedMinutes,

            // LLM reasoning trace (for logging)
            String thought
    ) {}
}

