package org.processmining.plugins.converters;

import java.util.*;

import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;

public class FlexFixer {

    private final Flex causalNet;
    private final Collection<SetFlex> declaredStartNodes;
    private final Collection<SetFlex> declaredEndNodes;
    private FlexNode commonStartNode;
    private FlexNode commonEndNode;

    public FlexFixer(Flex causalNet, Collection<SetFlex> declaredStartNodes, Collection<SetFlex> declaredEndNodes) {
        if (causalNet == null) throw new IllegalArgumentException("'causalNet' is null");
        this.causalNet = causalNet;
        this.declaredStartNodes = declaredStartNodes;
        this.declaredEndNodes = declaredEndNodes;
    }

    public void fixMultipleInputsAndOutputs() {
        Collection<SetFlex> startNodes = new ArrayList<SetFlex>();
        if (this.declaredStartNodes != null && !this.declaredStartNodes.isEmpty())
            startNodes.addAll(this.declaredStartNodes);
        else startNodes = findStartNodes();
        if (startNodes == null || startNodes.isEmpty())
            throw new IllegalStateException("No start nodes found in the C-net");

        Collection<SetFlex> endNodes = new ArrayList<SetFlex>();
        if (this.declaredEndNodes != null && !this.declaredEndNodes.isEmpty()) endNodes.addAll(this.declaredEndNodes);
        else endNodes = findEndNodes();
        if (endNodes == null || endNodes.isEmpty())
            throw new IllegalStateException("No end nodes found in the C-net");

        if (startNodes.size() > 1) addCommonStartNode(startNodes);
        else commonStartNode = startNodes.iterator().next().first();

        if (endNodes.size() > 1) addCommonEndNode(endNodes);
        else commonEndNode = endNodes.iterator().next().first();
    }

    private Collection<SetFlex> findStartNodes() {
        List<SetFlex> result = new ArrayList<SetFlex>();
        for (FlexNode node : causalNet.getNodes()) {
            Set<SetFlex> inputNodes = node.getInputNodes();
            if (inputNodes == null || inputNodes.isEmpty() ||
                (inputNodes.size() == 1 && inputNodes.iterator().next().isEmpty())) {
                SetFlex input = new SetFlex();
                input.add(node);
                result.add(input);
            }
        }
        return result;
    }

    private Collection<SetFlex> findEndNodes() {
        List<SetFlex> result = new ArrayList<SetFlex>();
        for (FlexNode node : causalNet.getNodes()) {
            Set<SetFlex> outputNodes = node.getOutputNodes();
            if (outputNodes == null || outputNodes.isEmpty() ||
                (outputNodes.size() == 1 && outputNodes.iterator().next().isEmpty())) {
                SetFlex output = new SetFlex();
                output.add(node);
                result.add(output);
            }
        }
        return result;
    }

    private void addCommonStartNode(Iterable<SetFlex> startNodes) {
        commonStartNode = causalNet.addNode("start");
        SetFlex commonInput = new SetFlex();
        commonInput.add(commonStartNode);
        for (SetFlex output : startNodes) {
            commonStartNode.addOutputNodes(output);
            for (FlexNode node : output) {
                boolean startNodeAdded = false;
                for (SetFlex input : node.getInputNodes()) {
                    if (input.contains(commonStartNode)) startNodeAdded = true;
                }
                if (!startNodeAdded) {
                    node.addInputNodes(commonInput);
                    causalNet.addArc(commonStartNode, node);
                }
            }
        }
    }

    private void addCommonEndNode(Iterable<SetFlex> endNodes) {
        commonEndNode = causalNet.addNode("end");
        SetFlex commonOutput = new SetFlex();
        commonOutput.add(commonEndNode);
        for (SetFlex input : endNodes) {
            commonEndNode.addInputNodes(input);
            for (FlexNode node : input) {
                boolean endNodeAdded = false;
                for (SetFlex output : node.getOutputNodes()) {
                    if (output.contains(commonEndNode)) endNodeAdded = true;
                }
                if (!endNodeAdded) {
                    node.addOutputNodes(commonOutput);
                    causalNet.addArc(node, commonEndNode);
                }
            }
        }
    }

    public void removeEmptyBindings() {
        for (FlexNode node : causalNet.getNodes()) {
            removeEmptySets(node.getInputNodes());
            removeEmptySets(node.getOutputNodes());
        }
    }

    private void removeEmptySets(Set<SetFlex> sets) {
        Iterator<SetFlex> iterator = sets.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().isEmpty()) iterator.remove();
        }
    }

    public FlexNode getCommonStartNode() {
        return commonStartNode;
    }

    public FlexNode getCommonEndNode() {
        return commonEndNode;
    }
}
