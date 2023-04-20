package com.krause.wikigir.main.models.general;


import org.tartarus.martin.Stemmer;

import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.*;

/**
 * Receives a body of text and tokenizes it into individual *english* words. The words are
 * lowercased and every character that is not a letter or digit is removed. Thie class
 * also contains a method to filter stop words from the result.
 *
 * @author Amir Krause
 */
public class TextTokenizer
{
    private static final Set<String> STOP_WORDS =
            new HashSet<>(Arrays.asList("www", "http", "com", "the", "be", "to", "of", "and", "a", "in",
                    "that", "have", "i", "it", "for", "not", "on", "with", "he", "as",
                    "you", "do", "at", "this", "but", "his", "by", "from", "they",
                    "we", "say", "her", "she", "or", "an", "will", "my", "one", "all",
                    "would", "there", "their", "what", "so", "up", "out", "if", "about",
                    "who", "get", "go", "which", "me", "when", "make", "can", "like",
                    "time", "no", "just", "him", "know", "take", "into", "year", "your",
                    "good", "some", "could", "them", "see", "other", "than", "then",
                    "now", "only", "come", "its", "over", "also", "back", "after",
                    "use", "two", "how", "our", "work", "first", "well", "way", "even",
                    "new", "want", "any", "these", "day", "most", "us", "because", "is",
                    "was", "are", "has", "were", "more", "been", "very", "where", "did",
                    "should", "", "may", "non"));

    private static final int MIN_WORD_LENGTH = 3;

    private static final Pattern NON_LETTERS = Pattern.compile("[^\\w ]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern DIGITS = Pattern.compile("^\\d+$");

    /**
     * Tokenizes the text.
     * @return the words list, tokenized.
     */
    public static List<String> tokenize(String text, boolean stem)
    {
        List<String> tokens = new ArrayList<>();

        Scanner scanner = new Scanner(removeAllPunctuation(text).replaceAll("\\.", " "));
        while(scanner.hasNext())
        {
            String word = scanner.next();

            word = NON_LETTERS.matcher(word).replaceAll("").toLowerCase().trim();

            // The word is purely digits, don't take it into account.
            if(DIGITS.matcher(word).find())
            {
                continue;
            }

            tokens.add(word);
        }

        // Use the porter stemmer to get stemmed versions of the words.
        if(stem)
        {
            tokens = stem(tokens);
        }

        return tokens;
    }

    /**
     * Filters stop words from the tokenized list of words.
     * @return the filtered list of words.
     */
    public static List<String> filterStopWords(List<String> tokens)
    {
        return tokens.stream().filter(w -> w.length() >=MIN_WORD_LENGTH &&
                !STOP_WORDS.contains(w)).collect(Collectors.toList());
    }

    private static String removeAllPunctuation(String text)
    {
        String tmp = text.replaceAll("['`;,?!]", "");
        return tmp.replaceAll("[_@\\-\t/\\\\]", " ");
    }

    // Use the porter stemmer to get stem versions of words.
    private static List<String> stem(List<String> tokens)
    {
        return tokens.stream().map(token ->
        {
            if(token == null)
            {
                return null;
            }

            Stemmer s = new Stemmer();
            for(int i = 0; i < token.length(); i++)
            {
                s.add(token.charAt(i));
            }

            s.stem();

            return s.toString();
        }).collect(Collectors.toList());
    }
}