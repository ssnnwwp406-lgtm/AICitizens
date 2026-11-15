
package com.aicitizens.utils;

import com.aicitizens.AICitizens;
import com.aicitizens.citizens.Citizen;
import com.aicitizens.citizens.CitizenManager;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RandomNames {

    private final AICitizens plugin;
    private final CitizenManager manager;

    private final Set<String> usedNames = new HashSet<>();

    private static final String[] FIRST_NAMES = {
            "James", "John", "Robert", "Michael",
            "William", "David", "Richard", "Joseph",
            "Thomas", "Charles", "Daniel", "Matthew",
            "Anthony", "Mark", "Steven", "Paul",
            "Andrew", "Joshua", "Kevin", "Brian"
    };

    private static final String[] LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown",
            "Jones", "Miller", "Davis", "Wilson",
            "Anderson", "Taylor", "Thomas", "Moore",
            "Jackson", "Martin", "Lee", "Thompson"
    };

    public RandomNames(AICitizens plugin, CitizenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public String generate() {

        if (!plugin.getConfig().getBoolean(
                "citizens.names.random", true
        )) {
            return "Citizen";
        }

        boolean preventDuplicates = plugin.getConfig().getBoolean(
                "citizens.names.prevent-duplicates", true
        );

        for (int i = 0; i < 100; i++) {

            String first = FIRST_NAMES[
                    ThreadLocalRandom.current().nextInt(FIRST_NAMES.length)
            ];

            String last = LAST_NAMES[
                    ThreadLocalRandom.current().nextInt(LAST_NAMES.length)
            ];

            String name = first + " " + last;

            if (!preventDuplicates || !isNameUsed(name)) {
                usedNames.add(name.toLowerCase(Locale.ROOT));
                return name;
            }
        }

        return "Citizen" + ThreadLocalRandom.current().nextInt(10000);
    }

    private boolean isNameUsed(String name) {

        if (usedNames.contains(name.toLowerCase(Locale.ROOT))) {
            return true;
        }

        for (Citizen citizen : manager.getCitizens()) {
            if (citizen.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    public void release(String name) {

        if (name != null) {
            usedNames.remove(name.toLowerCase(Locale.ROOT));
        }
    }
}
