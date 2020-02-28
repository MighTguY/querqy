/**
 *
 */
package querqy.rewrite.commonrules;

import querqy.infologging.InfoLoggingContext;
import querqy.model.*;
import querqy.model.Term;
import querqy.rewrite.ContextAwareQueryRewriter;
import querqy.rewrite.SearchEngineRequestAdapter;
import querqy.rewrite.commonrules.model.*;
import querqy.rewrite.commonrules.model.InputBoundary.Type;
import querqy.rewrite.commonrules.select.SelectionStrategy;
import querqy.rewrite.commonrules.select.TopRewritingActionCollector;

import java.util.*;

/**
 * @author rene
 *
 */
public class CommonRulesRewriter extends AbstractNodeVisitor<Node> implements ContextAwareQueryRewriter {

    static final InputBoundary LEFT_BOUNDARY = new InputBoundary(Type.LEFT);
    static final InputBoundary RIGHT_BOUNDARY = new InputBoundary(Type.RIGHT);
    public static final String APPLIED_RULES = "APPLIED_RULES";


    protected final RulesCollection rules;
    protected final LinkedList<PositionSequence<Term>> sequencesStack;
    protected ExpandedQuery expandedQuery;
    protected SearchEngineRequestAdapter searchEngineRequestAdapter;
    protected SelectionStrategy selectionStrategy;

    public CommonRulesRewriter(final RulesCollection rules,  final SelectionStrategy selectionStrategy) {
        this.rules = rules;
        sequencesStack = new LinkedList<>();
        this.selectionStrategy = selectionStrategy;
    }

    @Override
    public ExpandedQuery rewrite(ExpandedQuery query) {
        throw new UnsupportedOperationException("This rewriter needs a query context");
    }

    @Override
    public ExpandedQuery rewrite(ExpandedQuery query, Map<String, Object> context) {
        throw new UnsupportedOperationException("This rewriter needs a query adapter");
    }

    @Override
    public ExpandedQuery rewrite(ExpandedQuery query, SearchEngineRequestAdapter searchEngineRequestAdapter) {

        QuerqyQuery<?> userQuery = query.getUserQuery();

        if (userQuery instanceof Query) {

            this.expandedQuery = query;
            this.searchEngineRequestAdapter = searchEngineRequestAdapter;

            sequencesStack.add(new PositionSequence<>());

            super.visit((BooleanQuery) query.getUserQuery());

            applySequence(sequencesStack.removeLast(), true);

        }
        return query;
    }

   @Override
   public Node visit(BooleanQuery booleanQuery) {

      sequencesStack.add(new PositionSequence<>());

      super.visit(booleanQuery);

      applySequence(sequencesStack.removeLast(), false);

      return null;
   }

   protected void applySequence(final PositionSequence<Term> sequence, boolean addBoundaries) {

       final PositionSequence<InputSequenceElement> sequenceForLookUp = addBoundaries
               ? addBoundaries(sequence) : termSequenceToInputSequence(sequence);

       final boolean isDebug = Boolean.TRUE.equals(searchEngineRequestAdapter.getContext().get(CONTEXT_KEY_DEBUG_ENABLED));
       List<String> actionsDebugInfo = (List<String>) searchEngineRequestAdapter.getContext().get(CONTEXT_KEY_DEBUG_DATA);
       // prepare debug info context object if requested
       if (isDebug && actionsDebugInfo == null) {
           actionsDebugInfo = new LinkedList<>();
           searchEngineRequestAdapter.getContext().put(CONTEXT_KEY_DEBUG_DATA, actionsDebugInfo);
       }

       final TopRewritingActionCollector collector = selectionStrategy.createTopRewritingActionCollector();
       rules.collectRewriteActions(sequenceForLookUp, collector);

       final List<Action> actions = collector.createActions();


       final InfoLoggingContext infoLoggingContext = searchEngineRequestAdapter.getInfoLoggingContext().orElse(null);
       final boolean infoLoggingEnabled = infoLoggingContext != null && infoLoggingContext.isEnabledForRewriter();

       final Set<String> appliedRules = infoLoggingEnabled ? new HashSet<>() : null;

       for (Action action : actions) {
           if (isDebug) {
               actionsDebugInfo.add(action.toString());
           }

           final Instructions instructions = action.getInstructions();
           instructions.forEach(instruction ->

                           instruction.apply(sequence, action.getTermMatches(),
                               action.getStartPosition(),
                               action.getEndPosition(), expandedQuery, searchEngineRequestAdapter)


           );

           if (infoLoggingEnabled) {

               instructions.getProperty(Instructions.StandardPropertyNames.LOG_MESSAGE)
                       .map(String::valueOf).ifPresent(appliedRules::add);

           }

       }


       if (infoLoggingEnabled && !appliedRules.isEmpty()) {
           final Map<String, Set<String>> message = new IdentityHashMap<>(1);
           message.put(APPLIED_RULES, appliedRules);
           infoLoggingContext.log(message);
       }


   }

   protected PositionSequence<InputSequenceElement> termSequenceToInputSequence(PositionSequence<Term> sequence) {
       PositionSequence<InputSequenceElement> result = new PositionSequence<>();
       for (List<Term> termList : sequence) {
           result.add(Collections.<InputSequenceElement>unmodifiableList(termList));
       }
       return result;
   }
   protected PositionSequence<InputSequenceElement> addBoundaries(PositionSequence<Term> sequence) {

       PositionSequence<InputSequenceElement> result = new PositionSequence<>();
       result.nextPosition();
       result.addElement(LEFT_BOUNDARY);

       for (List<Term> termList : sequence) {
           result.add(Collections.<InputSequenceElement>unmodifiableList(termList));
       }

       result.nextPosition();
       result.addElement(RIGHT_BOUNDARY);
       return result;
   }

   @Override
   public Node visit(DisjunctionMaxQuery disjunctionMaxQuery) {
      sequencesStack.getLast().nextPosition();
      return super.visit(disjunctionMaxQuery);
   }

   @Override
   public Node visit(Term term) {
      sequencesStack.getLast().addElement(term);
      return super.visit(term);
   }


}
