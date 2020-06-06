package querqy.rewrite.contrib.replace;

import querqy.CompoundCharSequence;
import querqy.LowerCaseCharSequence;
import querqy.model.DisjunctionMaxQuery;
import querqy.model.Query;
import querqy.model.Term;
import querqy.parser.QuerqyParser;
import querqy.rewrite.commonrules.RuleParseException;
import querqy.trie.SequenceLookup;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ReplaceRewriterParser {

    private final InputStreamReader inputStreamReader;
    private final boolean ignoreCase;
    private final String inputDelimiter;
    private final QuerqyParser querqyParser;

    private static final String ERROR_MESSAGE_IMPROPER_INPUT_TEMPLATE = "ReplaceRule not properly configured for rule %s. \n" +
            "Each non-empty line must either start with # or " +
            "contain a rule with at least one input and one output, e. g. a => b\n" +
            "For suffix and prefix rules, only one input can be defined per output, e. g. a* => b\n" +
            "The wildcard cannot be defined multiple times in the same rule, a definition like *a* => b is not allowed." +
            "The wildcard cannot be used as a standalone input, a definition like * => b is not allowed.\n" +
            "The wildcard match can be added to the output of a suffix or prefix rule using $1, e. g. a* => a $1 or " +
            "*a => $1b.";

    private static final String ERROR_MESSAGE_DUPLICATE_INPUT_TEMPLATE = "Duplicate input: %s";

    private static final String OPERATOR = "=>";
    private static final String WILDCARD = "*";

    private String errorMessageImproperInput = "";
    private String errorMessageDuplicateInput = "";

    public ReplaceRewriterParser(final InputStreamReader inputStreamReader,
                                 final boolean ignoreCase,
                                 final String inputDelimiter,
                                 final QuerqyParser querqyParser) {

        this.inputStreamReader = inputStreamReader;
        this.ignoreCase = ignoreCase;
        this.inputDelimiter = inputDelimiter;
        this.querqyParser = querqyParser;
    }

    public SequenceLookup<ReplaceInstruction> parseConfig() throws IOException {

        final SequenceLookup<ReplaceInstruction> sequenceLookup = new SequenceLookup<>(ignoreCase);
        final Set<CharSequence> checkForDuplicateInput = new HashSet<>();

        try (final BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {

                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                errorMessageImproperInput = String.format(ERROR_MESSAGE_IMPROPER_INPUT_TEMPLATE, line);

                throwIfTrue(!line.contains(OPERATOR), errorMessageImproperInput);

                final String[] lineSplit = line.split(OPERATOR);
                throwIfTrue(lineSplit.length < 1 || lineSplit.length > 2, errorMessageImproperInput);

                final String fullInput = lineSplit[0].trim();
                throwIfTrue(fullInput.isEmpty(), errorMessageImproperInput);

                final String output = lineSplit.length == 2 ? lineSplit[1].trim() : "";

                errorMessageDuplicateInput = String.format(ERROR_MESSAGE_DUPLICATE_INPUT_TEMPLATE, fullInput);

                final List<LinkedList<String>> inputs = parseInput(fullInput);
                final LinkedList<String> outputList = parseOutput(output);

                for (final LinkedList<String> input : inputs) {

                    if (fullInput.startsWith(WILDCARD) && fullInput.length() > 1) {

                        throwIfTrue(fullInput.endsWith(WILDCARD), errorMessageImproperInput);
                        throwIfTrue(input.size() != 1, errorMessageImproperInput);

                        final CharSequence seq = lc(input.get(0));
                        throwIfTrue(checkForDuplicateInput.contains(seq), errorMessageDuplicateInput);
                        checkForDuplicateInput.add(seq);

                        sequenceLookup.putSuffix(
                                seq.subSequence(1, seq.length()),
                                new WildcardReplaceInstruction(outputList)
                        );

                    } else if (fullInput.endsWith(WILDCARD) && fullInput.length() > 1) {

                        throwIfTrue(input.size() != 1, errorMessageImproperInput);

                        final CharSequence seq = lc(input.get(0));
                        throwIfTrue(checkForDuplicateInput.contains(seq), errorMessageDuplicateInput);
                        checkForDuplicateInput.add(seq);

                        sequenceLookup.putPrefix(
                                seq.subSequence(0, fullInput.length() - 1),
                                new WildcardReplaceInstruction(outputList)
                        );

                    } else {

                        throwIfTrue(input.stream().anyMatch(term ->
                                term.startsWith(WILDCARD) || term.endsWith(WILDCARD)), errorMessageImproperInput);
                        final List<CharSequence> seqList = lc(input);

                        final CharSequence seq = new CompoundCharSequence(" ", seqList);
                        throwIfTrue(checkForDuplicateInput.contains(seq), errorMessageDuplicateInput);
                        checkForDuplicateInput.add(seq);

                        sequenceLookup.put(
                                seqList,
                                new TermsReplaceInstruction(outputList)
                        );
                    }
                }
            }

        } catch (RuleParseException e) {
            throw new IOException(e);
        }

        return sequenceLookup;
    }

    private CharSequence lc(final String seq) {
        return ignoreCase ? new LowerCaseCharSequence(seq) : seq;
    }

    private List<CharSequence> lc(final List<String> seq) {
        return seq.stream().map(this::lc).collect(Collectors.toList());
    }

    private void throwIfTrue(final boolean bool, final String message) throws RuleParseException {
        if (bool) {
            throw new RuleParseException(message);
        }
    }

    private LinkedList<String> parseOutput(final String term) {
        return parseQuery(this.querqyParser.parse(term));
    }

    private List<LinkedList<String>> parseInput(final String fullInput) throws RuleParseException {
        final List<String> inputs = Arrays.stream(fullInput.split(this.inputDelimiter))
                .map(String::trim)
                .filter(term -> !term.isEmpty())
                .collect(Collectors.toList());

        throwIfTrue(inputs.isEmpty(), errorMessageImproperInput);

        return inputs.stream()
                .map(this.querqyParser::parse)
                .map(this::parseQuery)
                .collect(Collectors.toList());
    }

    private LinkedList<String> parseQuery(final Query query) {
        return query.getClauses().stream()
                .map(booleanClause -> (DisjunctionMaxQuery) booleanClause)
                .flatMap(disjunctionMaxQuery -> disjunctionMaxQuery.getTerms().stream())
                .map(Term::getValue)
                .map(CharSequence::toString)
                .collect(Collectors.toCollection(LinkedList::new));
    }
}
