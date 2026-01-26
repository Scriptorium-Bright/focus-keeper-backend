package com.adhd.focusmate.service.ai;

import com.adhd.focusmate.dto.ai.TaskChunkResponse;
import com.adhd.focusmate.dto.ai.TaskSplitRequest;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

@Service
public class TaskAiService {

    private final ChatClient chatClient;

    public TaskAiService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public TaskChunkResponse splitTask(TaskSplitRequest request) {
        BeanOutputConverter<TaskChunkResponse> parser = new BeanOutputConverter<>(TaskChunkResponse.class);

        PromptTemplate template = getPromptTemplate();
        template.add("energyLevel", request.energyLevel());
        template.add("format", parser.getFormat());

        String userMessage = "User Goal: " + request.userGoal();

        String response = chatClient.prompt()
                .system(template.render())
                .user(userMessage)
                .call()
                .content();

        return parser.convert(response);
    }

    private static PromptTemplate getPromptTemplate() {
        String systemInstruction = """
                You are an expert ADHD coach. Break down simple tasks into atomic, actionable steps.

                Condition based on Energy Level ({energyLevel}/100):
                - If energy < 30: The user is exhausted. Break steps into micro-tasks (2-5 mins max). Be extremely encouraging and gentle.
                - If energy > 70: The user is energetic. Provide standard efficient steps (15-30 mins). Be direct.
                - Otherwise: Balanced approach (5-15 mins).

                {format}
                """;

        return new PromptTemplate(systemInstruction);
    }
}
