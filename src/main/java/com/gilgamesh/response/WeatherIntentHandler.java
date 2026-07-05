package com.gilgamesh.response;

import com.gilgamesh.model.Intent;

import java.util.List;
import java.util.Random;

/**
 * Dynamically handles weather queries using mock data.
 */
public class WeatherIntentHandler implements IntentHandler {

    private final Random random = new Random();
    private static final List<String> MOCK_CONDITIONS = List.of(
            "sunny and 75°F (24°C)", 
            "cloudy with a chance of rain", 
            "raining heavily", 
            "a crisp 50°F (10°C) and clear",
            "stormy with thunder and lightning"
    );

    @Override
    public boolean canHandle(Intent intent) {
        return intent != null && "weather".equals(intent.getTag());
    }

    @Override
    public String handle(Intent intent) {
        String condition = MOCK_CONDITIONS.get(random.nextInt(MOCK_CONDITIONS.size()));
        return "I'm checking my simulated weather sensors... It looks like it is currently " + condition + " out there!";
    }
}
