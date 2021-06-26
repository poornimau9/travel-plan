package com.example.demo;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;

public class PrepareItinerary {
    private final static String CITY_FILE_URL = "https://s3.us-west-2.amazonaws.com/secure.notion-static.com/4be05480-e7fc-4b41-b642-fb26dcaa4c39/cities.json?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAT73L2G45O3KS52Y5%2F20210623%2Fus-west-2%2Fs3%2Faws4_request&X-Amz-Date=20210623T053621Z&X-Amz-Expires=86400&X-Amz-Signature=6c50a82da7a260025d339b75e779ac67ad494a786f375a91e52434ef02ba7df7&X-Amz-SignedHeaders=host&response-content-disposition=filename%20%3D%22cities.json%22";
    private final static String CONTINENT_KEY = "contId";
    public static final String LOCATION = "location";
    public static final String LATITUDE = "lat";
    public static final String LONGITUDE = "lon";
    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String COMMA = ", ";
    public static final String ARROW = " -> ";
    public static final String BRACKET_OPEN = " (";
    public static final String BRACKET_CLOSE = ") ";

    public static void main(String[] args) {
        BufferedReader read = null;
        Scanner sc = new Scanner(System.in);
        System.out.println("Enter the city code: ");
        String sourceCity = sc.nextLine();
        try {
            URL url = new URL(CITY_FILE_URL);
            read = new BufferedReader(new InputStreamReader(url.openStream()));
            JSONObject cityJson = new JSONObject(read.readLine());

            if (null == cityJson.get(sourceCity)) {
                System.out.println("Incorrect Source City");
                throw new Exception(); // can use custom exception here
            }

            JSONObject source = cityJson.getJSONObject(sourceCity);
            Double sourceLat = source.getJSONObject(LOCATION).getDouble(LATITUDE);
            Double sourceLon = source.getJSONObject(LOCATION).getDouble(LONGITUDE);

            String sourceContinent = source.get(CONTINENT_KEY).toString();
            StringBuffer itinerary = new StringBuffer();
            String sourceString = sourceCity + BRACKET_OPEN + source.get(NAME) + COMMA + sourceCity + BRACKET_CLOSE;
            itinerary.append(sourceString);

            double distanceTravelled = 0.0d;
            Map<String, List<JSONObject>> cityByContinentMap = getCityByContinentMap(cityJson, sourceContinent);

            Map<String, JSONObject> continentCoordinates = getContinentCoordinates(cityByContinentMap);

            List<String> shortestPathContinents = findShortestPathContinents(sourceContinent, continentCoordinates);

            for (String continentId : shortestPathContinents) {
                //finding shortest city in given continent
                itinerary.append(ARROW);
                KDNode shortestDistanceCity = findShortestCityInContinent(source, cityByContinentMap.get(continentId));
                itinerary.append(shortestDistanceCity.cityCode).append(BRACKET_OPEN).append(shortestDistanceCity.cityName)
                        .append(COMMA).append(shortestDistanceCity.continentCode).append(BRACKET_CLOSE);
                //calculating total distance
                double[] coordinates = shortestDistanceCity.x;
                distanceTravelled += haversine(sourceLat, sourceLon, coordinates[0], coordinates[1]);
                sourceLat = coordinates[0];
                sourceLon = coordinates[1];
            }

            itinerary.append(ARROW).append(sourceString);
            System.out.println(itinerary);

            distanceTravelled += haversine(sourceLat, sourceLon, source.getJSONObject(LOCATION).getDouble(LATITUDE),
                    source.getJSONObject(LOCATION).getDouble(LONGITUDE));
            System.out.println("Distance travelled: " + distanceTravelled + " KMS");
        } catch (IOException | JSONException ex) {
            ex.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (null != read) {
                try {
                    read.close();
                } catch (IOException e) {

                }
            }
        }
    }

    /**
     * Calculating the average latitude and longitude of the continent
     *
     * @param cityByContinentMap
     * @return
     * @throws JSONException
     */
    private static Map<String, JSONObject> getContinentCoordinates(Map<String, List<JSONObject>> cityByContinentMap) throws JSONException {
        Map<String, JSONObject> continentCoordinates = new HashMap<>();
        double averageLat = 0;
        double averageLon = 0;
        int cityCount = 0;

        for (String continent : cityByContinentMap.keySet()) {
            for (JSONObject city : cityByContinentMap.get(continent)) {
                averageLat += city.getJSONObject(LOCATION).getDouble(LATITUDE);
                averageLon += city.getJSONObject(LOCATION).getDouble(LONGITUDE);
                cityCount++;
            }
            continentCoordinates.put(continent, new JSONObject().put(LATITUDE, averageLat / cityCount).put(LONGITUDE, averageLon / cityCount));
        }
        return continentCoordinates;
    }


    /**
     * Calculate the city with shortest distance to the source in the given continent
     * @param sourceCity
     * @param citiesList
     * @return
     * @throws JSONException
     */
    private static KDNode findShortestCityInContinent(JSONObject sourceCity, List<JSONObject> citiesList) throws JSONException {
        int cityLength = citiesList.size();
        KDTree shortestCityTree = new KDTree(cityLength);
        for (JSONObject city : citiesList) {
            double coordinates[] = new double[2];
            coordinates[0] = city.getJSONObject(LOCATION).getDouble(LATITUDE);
            coordinates[1] = city.getJSONObject(LOCATION).getDouble(LONGITUDE);
            shortestCityTree.add(coordinates, city.getString(CONTINENT_KEY), city.getString(ID), city.getString(NAME));
        }
        double sourceCoordinates[] = new double[2];
        sourceCoordinates[0] = sourceCity.getJSONObject(LOCATION).getDouble(LATITUDE);
        sourceCoordinates[1] = sourceCity.getJSONObject(LOCATION).getDouble(LONGITUDE);
        return shortestCityTree.find_nearest(sourceCoordinates);
    }


    /**
     * Find shortest path for a continent
     * @param sourceContId
     * @param continentCoordinates
     * @return
     * @throws JSONException
     */
    private static List<String> findShortestPathContinents(String sourceContId, Map<String, JSONObject> continentCoordinates)
            throws JSONException {
        Map<String, JSONObject> tempCoordinates = continentCoordinates;
        List<String> shortestPath = new ArrayList<>();

        double shortestDistance = Double.MAX_VALUE;
        double prevLat = tempCoordinates.get(sourceContId).getDouble(LATITUDE);
        double prevLon = tempCoordinates.get(sourceContId).getDouble(LONGITUDE);
        tempCoordinates.remove(sourceContId);
        String nearbyContId = sourceContId;

        while (tempCoordinates.size() > 0) {
            for (Map.Entry continent : tempCoordinates.entrySet()) {
                String contId = (String) continent.getKey();
                JSONObject location = (JSONObject) continent.getValue();
                double distanceFromSource = haversine(prevLat, prevLon, location.getDouble(LATITUDE),
                        location.getDouble(LONGITUDE));
                if (distanceFromSource < shortestDistance) {
                    shortestDistance = distanceFromSource;
                    nearbyContId = contId;
                }
            }
            shortestPath.add(nearbyContId);
            prevLat = tempCoordinates.get(nearbyContId).getDouble(LATITUDE);
            prevLon = tempCoordinates.get(nearbyContId).getDouble(LONGITUDE);
            tempCoordinates.remove(nearbyContId);
            shortestDistance = Double.MAX_VALUE;
        }
        return shortestPath;
    }

    /**
     * Convert the given json into a map which contains continent id as key and list of cities in that continent
     *
     * @param cityJson
     * @param sourceContinent
     * @return
     * @throws JSONException
     */
    private static Map<String, List<JSONObject>> getCityByContinentMap(JSONObject cityJson, String sourceContinent)
            throws JSONException {
        Map<String, List<JSONObject>> continentCityMap = new HashMap<>();
        Iterator cityKeys = cityJson.keys();
        while (cityKeys.hasNext()) {
            String next = cityKeys.next().toString();
            if (cityJson.get(next) instanceof JSONObject) {
                JSONObject city = cityJson.getJSONObject(next);
                String continentId = city.get(CONTINENT_KEY).toString();
                if (!continentCityMap.containsKey(continentId)) {
                    continentCityMap.put(continentId, new ArrayList<>());
                }
                continentCityMap.get(continentId).add(getCityJson(city));
            }
        }
        return continentCityMap;
    }


    /**
     * Keeping only required values from the Json
     *
     * @param city
     * @return
     * @throws JSONException
     */
    private static JSONObject getCityJson(JSONObject city) throws JSONException {
        JSONObject refactoredCityObject = new JSONObject();
        refactoredCityObject.put(ID, city.get(ID));
        refactoredCityObject.put(NAME, city.get(NAME));
        refactoredCityObject.put(LOCATION, city.get(LOCATION));
        refactoredCityObject.put(CONTINENT_KEY, city.get(CONTINENT_KEY));
        return refactoredCityObject;
    }

    /**
     * Formula to calculate the distance between provided lat long
     *
     * @param lat1
     * @param lon1
     * @param lat2
     * @param lon2
     * @return
     */
    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        // distance between latitudes and longitudes
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        // convert to radians
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        // apply formulae
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.pow(Math.sin(dLon / 2), 2) * Math.cos(lat1) *
                Math.cos(lat2);
        double rad = 6371;
        double c = 2 * Math.asin(Math.sqrt(a));
        return rad * c;
    }

}
