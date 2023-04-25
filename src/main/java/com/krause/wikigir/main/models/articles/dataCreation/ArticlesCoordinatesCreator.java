package com.krause.wikigir.main.models.articles.dataCreation;

import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.*;
import java.io.*;

import com.krause.wikigir.main.Constants;
import com.krause.wikigir.main.models.utils.*;
import com.krause.wikigir.main.models.general.WikiXMLArticlesExtractor;
import com.krause.wikigir.main.models.general.Coordinates;
import com.krause.wikigir.main.models.general.XMLParser;
import org.apache.commons.lang3.StringUtils;

/**
 * Extracts all *title* (main page's) coordinates from articles in the raw wikipedia XML file, then maps them to the
 * article title (in normalized form), creating the coordinates map.
 */
public class ArticlesCoordinatesCreator
{
    // The number of characters before and after the coordinates marking in the xml file (i.e.,
    // {{coord....}}) to look for &lt;!-- and --gt;, which mean these are commented coordinates.
    private static final int CHARS_TO_SCAN_FOR_TAGS = 10;

    // Main page coordinates can appear in the text in several forms:
    // {{coord|38|53|14.31|N|77|1|19.98|W|type:landmark|display=inline,title}}
    // {{coord|38|53|14.31|N|77|1|19.98|W|type:landmark|display=title,inline}}
    // {{Coord|44.532447|N|10.864137|E|display=title}}
    // {{Coord|44.532447|N|10.864137|E|display=it}}
    // What's important, is for the coordinates to have a "display=" attribute, then for at least on of these attributes
    // to be either "it" or "title" (with others being potentially "inline" or "text"). Since we are not sure which
    // other display locations there will be or if they will precede the wanted "title" or "it" values, we allow for
    // (potentially delimited) strings up to 3 times before trying to find the title - the important thing here is to
    // search for the title immediately after them and the "display" flag. This solves the problem of false positives
    // where the word "title" appears very far away from "display" and produces a minimal number of false negatives.
    private static final Pattern OUTER_PATTERN =
            Pattern.compile("\\{\\{(Wikidata)? *[Cc]oor.*?display *= *(([a-zA-Z]* *)[,;:./]? *){0,3}" +
                    "(([Ii][Tt])|([Tt][Ii][Tt][Ll][Ee])).*?}}");

    // Once the coordinates line has been found, search for the actual coordinates in the string.
    // This regex should return something like "|52|31|N|13|24|E|" or "|40.0|33.5|". Allow whitespaces in most
    // places to not miss malformed coordinates. In addition, allow termination with the "}" sign to capture
    // cases where the coordinates are placed at the end (e.g., {{Coord|display=title|34.0999|-117.6470}}).
    private static final Pattern INNER_PATTERN =
            Pattern.compile("\\| *(((-?\\d*(\\.\\d*)?)|N|n|S|s|W|w|E|e) *[|}] *)+");

    // The key value in the parser's result map where the coordinates are stored (if found).
    private static final String COORDINATES_KEY = "coordinates";

    // 0 = Parse all articles.
    private static final int ARTICLES_LIMIT = 0;

    private final String filePath;
    private final Map<String, Coordinates> coordinatesMapping;
    private final BlockingThreadFixedExecutor executor;

    /**
     * Constructor.
     */
    public ArticlesCoordinatesCreator()
    {
        this.filePath = GetFromConfig.filePath("wikigir.base_path", "wikigir.articles.folder",
                                               "wikigir.articles.articles_to_coordinates.file_name");

        this.coordinatesMapping = new HashMap<>();
        this.executor = new BlockingThreadFixedExecutor();
    }

    /**
     * Gets the mapping of article titles to (main) coordinates from file (if existing), or from the raw XML wiki file.
     * @return the mapping.
     */
    public Map<String, Coordinates> create()
    {
        if(new File(this.filePath).exists())
        {
            new Serializer().deserialize();
        }
        else
        {
            readFromXml();
            new Serializer().serialize();
        }

        return this.coordinatesMapping;
    }

    private void readFromXml()
    {
        int[] processed = {0};

        WikiXMLArticlesExtractor.extract(getCoordinatesParserFactory(),
            (parser, text) ->
                this.executor.execute(() ->
                {
                    ExceptionWrapper.wrap(() ->
                    {
                        ProgressBar.mark(processed, Constants.NUMBER_OF_ARTICLES);
                        parser.parse(text);

                        synchronized(ArticlesCoordinatesCreator.this)
                        {
                            if(parser.getTitle() != null)
                            {
                                if(parser.getResult().get(COORDINATES_KEY) != null)
                                {
                                    ArticlesCoordinatesCreator.this.coordinatesMapping.put(parser.getTitle(),
                                                       (Coordinates)parser.getResult().get(COORDINATES_KEY));
                                }
                            }
                        }
                    }, ExceptionWrapper.Action.IGNORE);
                }), ARTICLES_LIMIT);

        this.executor.waitForTermination();
    }

    // Creates a factory generating an implementation of the XML parser which attempts to extract
    // the main coordinates from a single wikipedia page. The main title coordinates' regex form
    // is known beforehand and from the extracted line, the actual categories are fetched. They
    // can be either in floating point notations or in NSWE notations - in either case, the parser
    // makes sure they are well formed and, when NSWE notation is encountered, translates the
    // coordinates to regular lat/lon floating point notation.
    private XMLParser.XMLParserFactory getCoordinatesParserFactory()
    {
        return new XMLParser.XMLParserFactory()
        {
            @Override
            public XMLParser getParser()
            {
                return new XMLParser()
                {
                    @Override
                    public void parse(StringBuilder sb)
                    {
                        this.addTitleToResult(sb);

                        Matcher m = OUTER_PATTERN.matcher(sb.toString());

                        while(m.find())
                        {
                            String line = sb.toString().substring(m.start(), m.end());

                            // Remove coordinates with the right format, but commented in the XML file (usually bad).
                            if(commentedCoords(sb, m.start(), m.end()))
                            {
                                continue;
                            }

                            // Coordinates on other planets or the moon are not important, at this time :)
                            if(line.contains("globe") && !line.contains("globe:earth"))
                            {
                                continue;
                            }

                            // Fix issues where phrases of the sort "&lt;!--42--&gt;" sneak into the coordinates.
                            line = line.replaceAll("&lt;.*?&gt;", "");

                            Matcher m2 = INNER_PATTERN.matcher(line);
                            if(m2.find())
                            {
                                Coordinates c = getCoordinates(line.substring(m2.start(), m2.end()));
                                if(c != null)
                                {
                                    this.result.put(COORDINATES_KEY, c);
                                }
                            }
                        }
                    }

                    // Some coordinates appear normal in the text, but are actually commented in the xml file
                    // in the following notation: "&lt;!-- ... --&gt;" (i.e. <!--...-->). Discard these.
                    private boolean commentedCoords(StringBuilder sb, int start, int end)
                    {
                        String preSection = sb.substring(Math.max(0, start - CHARS_TO_SCAN_FOR_TAGS), start);
                        String postSection = sb.substring(end, Math.min(end + CHARS_TO_SCAN_FOR_TAGS, sb.length()));
                        return preSection.toLowerCase().contains("&lt;") && postSection.toLowerCase().contains("&gt;");
                    }

                    private Coordinates getCoordinates(String coordsFromText)
                    {
                        // Trim the prefixing and suffixing "|" signs.
                        coordsFromText = coordsFromText.substring(1, coordsFromText.length() - 1);

                        List<String> parts = Arrays.asList(coordsFromText.split("\\|"));

                        // Get rid of whitespaces and create a canonical lowercase form for NSWE.
                        parts = parts.stream().filter(StringUtils::isNotEmpty).map(String::toLowerCase).
                                                         map(String::trim).collect(Collectors.toList());

                        if(parts.contains("e") || parts.contains("w"))
                        {
                            return coordinatesFromType1(parts);
                        }

                        return coordinatesFromType2(parts);
                    }

                    // This means the coordinates are given with NSWE notation - first check the format
                    // is valid and if so, translate the notation to regular floating point notation.
                    private Coordinates coordinatesFromType1(List<String> parts)
                    {
                        if(!validType1Coordinates(parts))
                        {
                            return null;
                        }

                        // Hours / minutes / seconds notation.
                        double[] lat = {0, 0, 0};
                        double[] lon = {0, 0, 0};

                        int i = 0;
                        for(; i < parts.size(); i++)
                        {
                            if(parts.get(i).startsWith("n") || parts.get(i).startsWith("s"))
                            {
                                break;
                            }

                            lat[i] = Double.parseDouble(parts.get(i));
                        }

                        i++;
                        int index = 0;
                        for(; i < parts.size() - 1; i++)
                        {
                            lon[index++] = Double.parseDouble(parts.get(i));
                        }

                        // The format in this case is hours / minutes / seconds.
                        double latitude = lat[0] + lat[1] / 60 + lat[2] / 3600;
                        double longitude = lon[0] + lon[1] / 60 + lon[2] / 3600;

                        latitude *= parts.contains("s") ? -1 : 1;
                        longitude *= parts.contains("w") ? -1 : 1;

                        return new Coordinates(latitude, longitude);
                    }

                    // The categories were given as floating point numbers, no NSWE notation.
                    private Coordinates coordinatesFromType2(List<String> parts)
                    {
                        if(parts.size() != 2)
                        {
                            return null;
                        }

                        return new Coordinates(Double.parseDouble(parts.get(0)),
                                Double.parseDouble(parts.get(1)));
                    }

                    // Parses a "type-1" coordinates (with NSWE notations) and verifies it has both a
                    // latitude and longitude notations, with a number of parts (minutes, seconds, etc.)
                    // not exceeding the maximal amount (3).
                    private boolean validType1Coordinates(List<String> parts)
                    {
                        boolean foundLat = false, foundLon = false;
                        int beforeLat = 0, beforeLon = 0;

                        int i = 0;
                        for(; i < parts.size(); i++)
                        {
                            if(parts.get(i).startsWith("s") || parts.get(i).startsWith("n"))
                            {
                                foundLat = true;
                                break;
                            }

                            beforeLat++;
                        }

                        i++;
                        for(; i < parts.size(); i++)
                        {
                            if(parts.get(i).startsWith("w") || parts.get(i).startsWith("e"))
                            {
                                foundLon = true;
                                break;
                            }

                            beforeLon++;
                        }

                        return foundLat && foundLon && beforeLat > 0 && beforeLat < 4 && beforeLon > 0 && beforeLon < 4;
                    }
                };
            }
        };
    }

    private class Serializer implements CustomSerializable
    {
        @Override
        public String filePath()
        {
            return ArticlesCoordinatesCreator.this.filePath;
        }

        @Override
        public void customSerialize(DataOutputStream out) throws IOException
        {
            out.writeInt(ArticlesCoordinatesCreator.this.coordinatesMapping.size());
            for(Map.Entry<String, Coordinates> e : ArticlesCoordinatesCreator.this.coordinatesMapping.entrySet())
            {
                out.writeUTF(e.getKey());
                Coordinates.serialize(out, e.getValue());
            }
        }

        @Override
        public void customDeserialize(DataInputStream in) throws IOException
        {
            int count = in.readInt();
            for(int i = 0; i < count; i++)
            {
                String title = in.readUTF();
                Coordinates coordinates = Coordinates.deserialize(in);
                ArticlesCoordinatesCreator.this.coordinatesMapping.put(title, coordinates);
            }
        }
    }

    public static void main(String[] args)
    {
        new ArticlesCoordinatesCreator().create();
    }
}