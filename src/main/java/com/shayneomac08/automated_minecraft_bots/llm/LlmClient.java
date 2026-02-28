package com.shayneomac08.automated_minecraft_bots.llm;

import com.shayneomac08.automated_minecraft_bots.agent.ActionPlan;

public interface LlmClient {
    ActionPlan plan(String prompt) throws Exception;
}
