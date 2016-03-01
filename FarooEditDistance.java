package editdistance;

import java.util.*;


/**
 * Created by mohan.gupta on 08/02/16.
 * A basic implementation of faroos.
 */
public class FarooEditDistance {

    private static int editDistanceMax = 2;
    private static int verbose = 0;
    private int maxOccuranceCount = 0; //max occurance count for any word seen.

    private Map<String, DictionaryItem> dictionary;
    // dictionary is keyed on the actual words and the edit keys for the given word.
    // for edit entry, the word and count field might be empty. (sometimes not as an edit might as well be an actual word as well).
    private static class DictionaryItem {
        String word; // the actual dictionary word.
        int count; //count of the dictionary word.
        //List of vocab terms w.r.t the dictionary key, //filled when this entry belongs to an edit key.
        List<EditItem> edits = new ArrayList<EditItem>();

    }

    // holder for an actual word in the vocab
    private static class EditItem {
        String word; // the parent word.
        int distance; // the distance w.r.t the edit key it is kept against.

        @Override
        public int hashCode() {
            return word.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return word.equals(((EditItem)obj).word);
        }
    }

    // The response for a query.
    public static class Suggestion implements Comparable<Suggestion>{
        String word;
        int count;
        int distance;
        double weightedDistance;
        @Override
        public int hashCode() {
            return word.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return word.equals(((Suggestion)obj).word);
        }

        public int compareTo(Suggestion o) {
            return (this.count - o.count);
        }
    }

    private void createDictionary() {
        if (this.dictionary == null) {
            this.dictionary = new HashMap<String, DictionaryItem>();
        }
    }


    // Break down into valid words and put to dictionary.
    private void addLine(String line, int count){
        List<String> words = getWordsForWordLevelModels(line);
        if (words == null) return;
        for (String word : words) {
            addWord(word, count);
        }
    }

    // While training the edit distance model or the phonetics model, break lines into words.
    private static List<String> getWordsForWordLevelModels(String line) {
        if (line == null || line.isEmpty()) {
            return Collections.emptyList();
        }
        // break line into tokens.
        line = line.toLowerCase().trim();
        return Arrays.asList(line.split(" "));
    }

    // add a word and its edits to the dictionary.
    private void addWord(String word, int count){
        // add a new item.
        if (!dictionary.containsKey(word)) {
            DictionaryItem item = new DictionaryItem();
            dictionary.put(word, item);
        }

        DictionaryItem item = dictionary.get(word);
        item.count += count;

        // update the maximum count seen, used to give boost to a dictionary word.
        if (item.count > maxOccuranceCount) {
            maxOccuranceCount = item.count;
        }

        // First time we have encountered the word.
        if (item.word == null) {
            item.word = word;
            //create deletes, the word fields in these delete are actually the string after the delete.
            for (EditItem delete : getEdits(word, 0, true))
            {
                EditItem suggestion = new EditItem();
                suggestion.word = word;
                suggestion.distance = delete.distance;

                DictionaryItem item2;
                if (dictionary.containsKey(delete.word))
                {
                    item2 = dictionary.get(delete.word);
                    //already exists:
                    //1. word1==deletes(word2)
                    //2. deletes(word1)==deletes(word2)
                    if (!item2.edits.contains(suggestion)) addLowestDistance(item2.edits, suggestion);
                }
                else
                {
                    item2 = new DictionaryItem();
                    item2.edits.add(suggestion);
                    dictionary.put(delete.word, item2);
                }
            }

        }

    }

    //save some time and space
    private static void addLowestDistance(List<EditItem> suggestions, EditItem suggestion)
    {
        //remove all existing suggestions of higher distance, if verbose<2
        if ((verbose < 2) && (suggestions.size() > 0) && (suggestions.get(0).distance > suggestion.distance)) suggestions.clear();
        //do not add suggestion of higher distance than existing, if verbose<2
        if ((verbose == 2) || (suggestions.size() == 0) || (suggestions.get(0).distance >= suggestion.distance)) suggestions.add(suggestion);
    }


    private List<EditItem> getEdits(String word, int editDistance, boolean isRecursive) {
        editDistance++;
        List<EditItem> deletes = new ArrayList<EditItem>();
        if (word.length() > 1) {
            char[] wordArray = word.toCharArray();
            StringBuilder builder = new StringBuilder(word);
            String deleteWord = null;
            for (int i = 0; i < word.length(); i++)
            {
                EditItem delete = new EditItem();

                char charAti = builder.charAt(i);
                builder.deleteCharAt(i);

                deleteWord = builder.toString();
                builder.insert(i, charAti);

                delete.word = deleteWord;
                delete.distance = editDistance;

                if (!deletes.contains(delete))
                {
                    deletes.add(delete);
                    //recursion, if maximum edit distance not yet reached
                    if (isRecursive && (editDistance < editDistanceMax))
                    {
                        for(EditItem edit1 : getEdits(delete.word, editDistance, isRecursive))
                        {
                            if (!deletes.contains(edit1)) deletes.add(edit1);
                        }
                    }
                }
            }
        }

        return deletes;

    }

    // For a word, we calculate all the edit distance keys and put them in a queue
    // for each key we look it up in the dictionary and get a list of vocab words with this key
    // and these vocab words in our suggestions, then we form more keys with higher distance for the given key
    // and add those additional keys in the key's queue.
    public List<Suggestion> getSuggestions(String word, int editDistanceMax, int maxSuggestions){

        int queueSize = 0;

        List<EditItem> candidates = new ArrayList<EditItem>();

        //add original term
        EditItem item = new EditItem();
        item.word = word;
        item.distance = 0;
        candidates.add(item);

        Set<Suggestion> suggestions = new HashSet<Suggestion>();

        DictionaryItem value;

        while (candidates.size()>0)
        {
            EditItem candidate = candidates.get(0);
            candidates.remove(0);

            //save some time
            //early termination
            //suggestion distance=candidate.distance... candidate.distance+editDistanceMax
            //if canddate distance is already higher than suggestion distance, than there are no better suggestions to be expected
            //if ((verbose < 2)&&(suggestions.size() > 0)&&(candidate.distance > suggestions.get(0).distance)) break;
            if (candidate.distance > editDistanceMax) break;


            if (dictionary.containsKey(candidate.word))
            {
                value = dictionary.get(candidate.word);
                // if the candidate is a dictionary word.
                if (value.word != null)
                {
                    //correct term
                    Suggestion si = new Suggestion();
                    si.word = value.word;
                    si.count = value.count;
                    si.distance = candidate.distance;

                    if (!suggestions.contains(si))
                    {
                        suggestions.add(si);
                        //When the actual word is found in the dictionary.
                        if (si.distance == 0) {
                            // As this seems to be a dictionary word, make sure that this is the top suggestion.
                            si.count = maxOccuranceCount;
                        }

                    }
                }

                // Find all the vocab words which are parents for this candidate word and add
                // then in dictionary.
                //edit term (with suggestions to correct term)
                DictionaryItem value2;
                for (EditItem edit : value.edits)
                {
                    int distance = TrueDistance(edit, candidate, word);

                    if (distance <= editDistanceMax)
                    {
                        // find the word's occurance count in the dictionary.
                        if (dictionary.containsKey(edit.word)) {
                            value2 = dictionary.get(edit.word);
                            Suggestion si = new Suggestion();
                            si.word = value2.word;
                            si.count = value2.count;
                            si.distance = distance;

                            suggestions.add(si);
                        }
                    }

                }

                // we have enough.
                if (suggestions.size() >= maxSuggestions) {
                    break;
                }
            }

            //form more keys with +1 edit distance and add them in the search queue.
            if (candidate.distance < editDistanceMax) {
                for (EditItem delete : getEdits(candidate.word, candidate.distance, false))
                {
                    if (!candidates.contains(delete)) candidates.add(delete);
                }
            }
            if (candidates.size() > queueSize) queueSize = candidates.size();
        }//end while

        List<Suggestion> sortedList = new ArrayList(suggestions);
        Collections.sort(sortedList, Collections.reverseOrder());

        return sortedList;
    }

    private static int TrueDistance(EditItem dictionaryOriginal, EditItem inputDelete, String inputOriginal)
    {
        //We allow simultaneous edits (deletes) of editDistanceMax on on both the dictionary and the input term.
        //For replaces and adjacent transposes the resulting edit distance stays <= editDistanceMax.
        //For inserts and deletes the resulting edit distance might exceed editDistanceMax.
        //To prevent suggestions of a higher edit distance, we need to calculate the resulting edit distance, if there are simultaneous edits on both sides.
        //Example: (bank==bnak and bank==bink, but bank!=kanb and bank!=xban and bank!=baxn for editDistanceMaxe=1)
        //Two deletes on each side of a pair makes them all equal, but the first two pairs have edit distance=1, the others edit distance=2.

        if (dictionaryOriginal.word == inputOriginal) return 0;
        else if (dictionaryOriginal.distance == 0) return inputDelete.distance;
        else if (inputDelete.distance == 0) return dictionaryOriginal.distance;
        else return computeLevenshteinDistance(dictionaryOriginal.word, inputOriginal);//adjust distance, if both distances>0
    }

    private static int computeLevenshteinDistance(CharSequence str1,
                                                 CharSequence str2) {
        int[][] distance = new int[str1.length() + 1][str2.length() + 1];

        for (int i = 0; i <= str1.length(); i++)
            distance[i][0] = i;
        for (int j = 0; j <= str2.length(); j++)
            distance[0][j] = j;

        for (int i = 1; i <= str1.length(); i++)
            for (int j = 1; j <= str2.length(); j++)
                distance[i][j] = minimum(
                        distance[i - 1][j] + 1,
                        distance[i][j - 1] + 1,
                        distance[i - 1][j - 1]
                                + ((str1.charAt(i - 1) == str2.charAt(j - 1)) ? 0
                                : 1));

        return distance[str1.length()][str2.length()];
    }

    private static int minimum(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }

    // read entires from map and add to dictionary. For each line we have the weight of the line in the input map.
    public void buildDictionary(Map<String, Integer> data){
        createDictionary();
        if (data != null) {
            for (String line : data.keySet()) {
                Integer count = data.get(line);
                if (count == null || count == 0) continue;
                addLine(line, count);
            }
        }
    };

    public static void main(String[] args) {
        FarooEditDistance editDistance = new FarooEditDistance();
        editDistance.addLine("brown fox jumped", 10);

        editDistance.getSuggestions("browne", 1,10);

    }

}
