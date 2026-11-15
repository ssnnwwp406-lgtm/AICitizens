
package com.aicitizens.utils;

import java.util.Random;

public final class RandomNames {

    private static final Random RANDOM = new Random();

    private static final String[] FIRST_NAMES = {
        "James", "John", "Robert", "Michael",
        "William", "David", "Richard", "Joseph",
        "Thomas", "Charles", "Daniel", "Matthew",
        "Anthony", "Mark", "Donald", "Steven",
        "Paul", "Andrew", "Joshua", "Kenneth",
        "Emily", "Olivia", "Sophia", "Emma",
        "Ava", "Isabella", "Mia", "Charlotte",
        "Amelia", "Harper", "Evelyn", "Abigail",
        "Elizabeth", "Sofia", "Grace", "Chloe"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown",
        "Jones", "Garcia", "Miller", "Davis",
        "Rodriguez", "Martinez", "Wilson", "Anderson",
        "Taylor", "Thomas", "Moore", "Jackson",
        "Martin", "Lee", "Perez", "Thompson",
        "White", "Harris", "Clark", "Lewis",
        "Robinson", "Walker", "Young", "Allen",
        "King", "Wright", "Scott", "Green"
    };

    private RandomNames() {
    }

    public static String generate() {

        String firstName = FIRST_NAMES[
            RANDOM.nextInt(FIRST_NAMES.length)
        ];

        String lastName = LAST_NAMES[
            RANDOM.nextInt(LAST_NAMES.length)
        ];

        return firstName + " " + lastName;
    }
}
