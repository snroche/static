package com.estatica.domain.world;

/** What it's like outside at a given moment. The session wires weather and the device clock. */
public interface World {

    /** Always a clear day: tests and anything that doesn't care. */
    World CALM = new World() {
        @Override
        public WeatherRules.Weather weatherAt(long millis) {
            return WeatherRules.Weather.CLEAR;
        }

        @Override
        public boolean nightAt(long millis) {
            return false;
        }

        @Override
        public double checkBonus(String stat, long millis) {
            return 0;
        }

        @Override
        public double noiseAt(long millis) {
            return 0;
        }
    };

    WeatherRules.Weather weatherAt(long millis);

    boolean nightAt(long millis);

    /** Check bonus for a stat at that moment (weather and darkness). */
    double checkBonus(String stat, long millis);

    /** Extra radio static at that moment. */
    double noiseAt(long millis);
}
