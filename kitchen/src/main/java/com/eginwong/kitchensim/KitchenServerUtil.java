package com.eginwong.kitchensim;

import com.eginwong.kitchensim.Kitchen.Meal;
import com.google.protobuf.util.JsonFormat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

import static com.eginwong.kitchensim.Kitchen.*;

public class KitchenServerUtil {

    /**
     * Gets the default features file from classpath.
     */
    public static URL getDefaultMealsFile() {
        return KitchenServer.class.getResource("kitchen_sim_meals_db.json");
    }

    /**
     * Parses the JSON input file containing the list of features.
     */
    public static List<Meal> parseKitchenMeals(URL file) throws IOException {
        InputStream input = file.openStream();
        try {
            Reader reader = new InputStreamReader(input, Charset.forName("UTF-8"));
            try {
                MealsDatabase.Builder database = MealsDatabase.newBuilder();
                JsonFormat.parser().merge(reader, database);
                return database.getMealsList();
            } finally {
                reader.close();
            }
        } finally {
            input.close();
        }
    }
}

