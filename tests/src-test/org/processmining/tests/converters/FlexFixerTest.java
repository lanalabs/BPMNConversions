package org.processmining.tests.converters;

import static org.junit.Assert.assertEquals;

import java.util.Collections;

import org.junit.Test;
import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexImpl;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;
import org.processmining.plugins.converters.FlexFixer;

public class FlexFixerTest {

    @Test(expected = IllegalArgumentException.class)
    public void constructor_withNullCausalNet_expectedException() {
        new FlexFixer(null, null, null);
    }

    @Test(expected = IllegalStateException.class)
    public void fixMultipleInputsAndOutputs_withEmptyCausalNet_expectedException() {
        Flex causalNet = new FlexImpl("label");
        FlexFixer fixer = new FlexFixer(causalNet, null, null);

        fixer.fixMultipleInputsAndOutputs();
    }

    @Test(expected = IllegalStateException.class)
    public void fixMultipleInputsAndOutputs_withNoInputs_expectedException() {
        Flex causalNet = createFlexWithNoInputs();
        FlexFixer fixer = new FlexFixer(causalNet, null, null);

        fixer.fixMultipleInputsAndOutputs();
    }

    private Flex createFlexWithNoInputs() {
        Flex result = new FlexImpl("label");

        FlexNode a = result.addNode("a");
        FlexNode b = result.addNode("b");
        FlexNode c = result.addNode("c");

        SetFlex aInput = new SetFlex();
        aInput.add(b);
        a.addInputNodes(aInput);
        SetFlex aOutput = new SetFlex();
        aOutput.add(b);
        a.addOutputNodes(aOutput);

        SetFlex bInput = new SetFlex();
        bInput.add(a);
        b.addInputNodes(bInput);
        SetFlex bOutput = new SetFlex();
        bOutput.add(c);
        b.addOutputNodes(bOutput);

        SetFlex cInput = new SetFlex();
        cInput.add(b);
        c.addInputNodes(cInput);

        result.addArc(a, b);
        result.addArc(b, a);
        result.addArc(b, c);

        return result;
    }

    @Test(expected = IllegalStateException.class)
    public void fixMultipleInputsAndOutputs_withNoOutputs_expectedException() {
        Flex causalNet = createFlexWithNoOutputs();
        FlexFixer fixer = new FlexFixer(causalNet, null, null);

        fixer.fixMultipleInputsAndOutputs();
    }

    private Flex createFlexWithNoOutputs() {
        Flex result = new FlexImpl("label");

        FlexNode a = result.addNode("a");
        FlexNode b = result.addNode("b");
        FlexNode c = result.addNode("c");

        a.addOutputNodes(setFlex(b));
        b.addInputNodes(setFlex(a));
        b.addInputNodes(setFlex(c));
        b.addOutputNodes(setFlex(c));
        c.addInputNodes(setFlex(b));
        c.addOutputNodes(setFlex(b));

        result.addArc(a, b);
        result.addArc(b, c);
        result.addArc(c, b);

        return result;
    }

    private SetFlex setFlex(FlexNode... nodes) {
        SetFlex result = new SetFlex();
        Collections.addAll(result, nodes);
        return result;
    }

    @Test
    public void fixMultipleInputsAndOutputs_withTwoInputsAndOneOutput_expectedCommonInput() {
        // assign
        Flex causalNet = new FlexImpl("label");

        FlexNode a = causalNet.addNode("a");
        FlexNode b = causalNet.addNode("b");
        FlexNode c = causalNet.addNode("c");
        FlexNode d = causalNet.addNode("d");

        a.addOutputNodes(setFlex(c));
        b.addOutputNodes(setFlex(c));
        c.addInputNodes(setFlex(a));
        c.addInputNodes(setFlex(a, b));
        c.addOutputNodes(setFlex(d));
        d.addInputNodes(setFlex(c));

        causalNet.addArc(a, c);
        causalNet.addArc(b, c);
        causalNet.addArc(c, d);

        FlexFixer fixer = new FlexFixer(causalNet, null, null);

        // act
        fixer.fixMultipleInputsAndOutputs();

        // assert
        assertEquals(5, causalNet.getNodes().size());

        FlexNode start = getSingleStartNode(causalNet);

        assertEquals(1, a.getInputNodes().size());
        assertEquals(start, a.getInputNodes().iterator().next().first());

        assertEquals(1, b.getInputNodes().size());
        assertEquals(start, b.getInputNodes().iterator().next().first());
    }

    private FlexNode getSingleStartNode(Flex causalNet) {
        for (FlexNode node : causalNet.getNodes()) {
            if (node.getInputNodes().isEmpty())
                return node;
        }
        return null;
    }

    @Test
    public void fixMultipleInputsAndOutputs_withOneInputAndTwoOutputs_expectedCommonInput() {
        // assign
        Flex causalNet = new FlexImpl("label");

        FlexNode a = causalNet.addNode("a");
        FlexNode b = causalNet.addNode("b");
        FlexNode c = causalNet.addNode("c");
        FlexNode d = causalNet.addNode("d");

        a.addOutputNodes(setFlex(b));
        b.addInputNodes(setFlex(a));
        b.addOutputNodes(setFlex(c));
        b.addOutputNodes(setFlex(c, d));
        c.addInputNodes(setFlex(b));
        d.addInputNodes(setFlex(b));

        causalNet.addArc(a, b);
        causalNet.addArc(b, c);
        causalNet.addArc(b, d);

        FlexFixer fixer = new FlexFixer(causalNet, null, null);

        // act
        fixer.fixMultipleInputsAndOutputs();

        // assert
        assertEquals(5, causalNet.getNodes().size());

        FlexNode end = getSingleEndNode(causalNet);

        assertEquals(1, c.getOutputNodes().size());
        assertEquals(end, c.getOutputNodes().iterator().next().first());

        assertEquals(1, d.getOutputNodes().size());
        assertEquals(end, d.getOutputNodes().iterator().next().first());
    }

    private FlexNode getSingleEndNode(Flex causalNet) {
        for (FlexNode node : causalNet.getNodes()) {
            if (node.getOutputNodes().isEmpty())
                return node;
        }
        return null;
    }
}
