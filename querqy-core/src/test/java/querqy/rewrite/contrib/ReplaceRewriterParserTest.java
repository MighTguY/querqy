package querqy.rewrite.contrib;

import org.junit.Test;
import querqy.ComparableCharSequenceWrapper;
import querqy.CompoundCharSequence;
import querqy.parser.WhiteSpaceQuerqyParser;
import querqy.trie.States;
import querqy.trie.TrieMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReplaceRewriterParserTest {

    @Test(expected = IOException.class)
    public void testWrongConfigurationDuplicateInputStringCaseInsensitive() throws IOException {
        String rules = "# comment\n"
                + "c => d \n"
                + " a   B => b \n"
                + "e d \t a b => c";

        InputStreamReader input = new InputStreamReader(new ByteArrayInputStream(rules.getBytes()));
        ReplaceRewriterParser replaceRewriterParser = new ReplaceRewriterParser(
                input, true, "\t", new WhiteSpaceQuerqyParser());
        replaceRewriterParser.parseConfig();
    }

    @Test
    public void testWrongConfigurationDuplicateInputStringCaseSensitive() throws IOException {
        String rules = "# comment\n"
                + "c => d \n"
                + " a   B => b \n"
                + "e d \t a b => c";

        InputStreamReader input = new InputStreamReader(new ByteArrayInputStream(rules.getBytes()));
        ReplaceRewriterParser replaceRewriterParser = new ReplaceRewriterParser(
                input, false, "\t", new WhiteSpaceQuerqyParser());
        replaceRewriterParser.parseConfig();
    }

    @Test(expected = IOException.class)
    public void testWrongConfiguration() throws IOException {
        String rules = "# comment\n"
                + "something wrong \n"
                + " FG => hi jk  \n ";

        InputStreamReader input = new InputStreamReader(new ByteArrayInputStream(rules.getBytes()));
        ReplaceRewriterParser replaceRewriterParser = new ReplaceRewriterParser(
                input, true, "\t", new WhiteSpaceQuerqyParser());
        replaceRewriterParser.parseConfig();
    }

    @Test
    public void testMappingCaseInsensitive() throws IOException {
        String rules = "# comment\n"
                + "\n"
                + " ab  \t c d => e \n"
                + " FG => hi jk  \n ";

        InputStreamReader input = new InputStreamReader(new ByteArrayInputStream(rules.getBytes()));
        ReplaceRewriterParser replaceRewriterParser = new ReplaceRewriterParser(
                input, true, "\t", new WhiteSpaceQuerqyParser());

        TrieMap<List<CharSequence>> trieMap = replaceRewriterParser.parseConfig();

        States<List<CharSequence>> match = trieMap.get("ab");
        assertTrue(match.getStateForCompleteSequence().isKnown);
        assertTrue(match.getStateForCompleteSequence().isFinal());
        assertEquals(new ComparableCharSequenceWrapper("e"),
                match.getStateForCompleteSequence().value.get(0));

        match = trieMap.get("c");
        assertTrue(match.getStateForCompleteSequence().isKnown);
        assertFalse(match.getStateForCompleteSequence().isFinal());
        assertNull(match.getStateForCompleteSequence().value);

        match = trieMap.get(new CompoundCharSequence(" ", Arrays.asList("c", "d")));
        assertTrue(match.getStateForCompleteSequence().isKnown);
        assertTrue(match.getStateForCompleteSequence().isFinal());
        assertEquals(new ComparableCharSequenceWrapper("e"),
                match.getStateForCompleteSequence().value.get(0));

        match = trieMap.get(new CompoundCharSequence(" ", Arrays.asList("c", "d")));
        assertTrue(match.getStateForCompleteSequence().isKnown);
        assertTrue(match.getStateForCompleteSequence().isFinal());
        assertEquals(new ComparableCharSequenceWrapper("e"),
                match.getStateForCompleteSequence().value.get(0));

        match = trieMap.get("fg");
        assertTrue(match.getStateForCompleteSequence().isKnown);
        assertTrue(match.getStateForCompleteSequence().isFinal());
        assertEquals(2, match.getStateForCompleteSequence().value.size());
        assertEquals(new ComparableCharSequenceWrapper("hi"),
                match.getStateForCompleteSequence().value.get(0));
        assertEquals(new ComparableCharSequenceWrapper("jk"),
                match.getStateForCompleteSequence().value.get(1));
    }

    @Test
    public void testMappingCaseSensitive() throws IOException {
        String rules = "AB => cd";

        InputStreamReader input = new InputStreamReader(new ByteArrayInputStream(rules.getBytes()));
        ReplaceRewriterParser replaceRewriterParser = new ReplaceRewriterParser(
                input, false, "\t", new WhiteSpaceQuerqyParser());

        TrieMap<List<CharSequence>> trieMap = replaceRewriterParser.parseConfig();

        States<List<CharSequence>> match = trieMap.get("ab");
        assertFalse(match.getStateForCompleteSequence().isKnown);

        match = trieMap.get("AB");
        assertTrue(match.getStateForCompleteSequence().isKnown);
        assertEquals(new ComparableCharSequenceWrapper("cd"),
                match.getStateForCompleteSequence().value.get(0));
    }
}
