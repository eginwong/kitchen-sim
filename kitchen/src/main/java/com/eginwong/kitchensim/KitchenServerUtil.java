package com.eginwong.kitchensim;

import com.eginwong.kitchensim.Kitchen.Meal;
import com.google.protobuf.util.JsonFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import static com.eginwong.kitchensim.Kitchen.MealsDatabase;

public class KitchenServerUtil {

    /**
     * Gets the default features file from classpath.
     */
    static URL getDefaultMealsFile() {
        return KitchenServer.class.getResource("kitchen_sim_meals_db.json");
    }

    /**
     * Parses the JSON input file containing the list of features.
     */
    static Map<Integer, Meal> parseKitchenMeals(URL file) throws IOException {
        try (InputStream input = file.openStream()) {
            try (Reader reader = new InputStreamReader(input, Charset.forName("UTF-8"))) {
                MealsDatabase.Builder database = MealsDatabase.newBuilder();
                JsonFormat.parser().merge(reader, database);

                Map<Integer, Meal> map = new HashMap<>();
                for (Meal m : database.getMealsList()) map.put(m.getId(), m);
                return map;
            }
        }
    }
}

