package org.processmining.plugins.converters;

import java.util.Collection;
import java.util.HashSet;

import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.contexts.uitopia.annotations.UITopiaVariant;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.plugin.annotations.Plugin;
import org.processmining.framework.plugin.annotations.PluginVariant;
import org.processmining.models.connections.flexiblemodel.FlexEndTaskNodeConnection;
import org.processmining.models.connections.flexiblemodel.FlexStartTaskNodeConnection;
import org.processmining.models.flexiblemodel.EndTaskNodesSet;
import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.SetFlex;
import org.processmining.models.flexiblemodel.StartTaskNodesSet;
import org.processmining.models.graphbased.directed.bpmn.BPMNDiagram;

/**
 * Plugin for conversion of Casual nets to BPMN with ORs
 * 
 * @author Anna Kalenkova
 *
 */
@Plugin(name = "Convert C-Net to BPMN with OR", parameterLabels = {"Causal Net"}, returnLabels = {"BPMN Diagram"},
        returnTypes = {BPMNDiagram.class}, userAccessible = true, help = "Converts C-Net to BPMN")
public class FlexToBPMNWithORConversionPlugin {

    @UITopiaVariant(affiliation = "HSE", author = "Anna Kalenkova", email = "akalenkova@hse.ru")
    @PluginVariant(variantLabel = "BPMN 2.0 Conversion", requiredParameterLabels = {0})
    public BPMNDiagram converter(UIPluginContext context, Flex model) {

        Collection<SetFlex> startNodes = retrieveStartNodes(context, model);
        Collection<SetFlex> endNodes = retrieveEndNodes(context, model);

        FlexFixer fixer = new FlexFixer(model, startNodes, endNodes);
        fixer.removeEmptyBindings();
        fixer.fixMultipleInputsAndOutputs();

        SetFlex startFlex = new SetFlex();
        startFlex.add(fixer.getCommonStartNode());
        SetFlex endFlex = new SetFlex();
        endFlex.add(fixer.getCommonEndNode());
        StartTaskNodesSet newStartSet = new StartTaskNodesSet();
        newStartSet.add(startFlex);
        EndTaskNodesSet newEndSet = new EndTaskNodesSet();
        newEndSet.add(endFlex);


        context.addConnection(
                new FlexStartTaskNodeConnection("Start tasks node of " + model.getLabel() + " connection", model,
                                                newStartSet)
        );
        context.addConnection(
                new FlexEndTaskNodeConnection("End tasks node of " + model.getLabel() + " connection", model, newEndSet)
        );

        FlexToBPMNWithORConverter converter = new FlexToBPMNWithORConverter(model);
        BPMNDiagram diagram = converter.convert();
        BPMNUtils.simplifyBPMNDiagram(converter.getConversionMap(), diagram);
        return diagram;
    }

    private Collection<SetFlex> retrieveStartNodes(UIPluginContext context, Flex model) {
        Collection<SetFlex> result = new HashSet<SetFlex>();
        try {
            FlexStartTaskNodeConnection startTaskNodeConnection = context.getConnectionManager().getFirstConnection(
                    FlexStartTaskNodeConnection.class, context, model);
            StartTaskNodesSet startTaskNodesSet =
                    startTaskNodeConnection.getObjectWithRole(FlexStartTaskNodeConnection.STARTTASKNODES);
            for (SetFlex startSet : startTaskNodesSet)
                result.add(startSet);
        } catch (ConnectionCannotBeObtained e) {
            e.printStackTrace();
        }
        return result;
    }

    private Collection<SetFlex> retrieveEndNodes(UIPluginContext context, Flex model) {
        Collection<SetFlex> result = new HashSet<SetFlex>();
        try {
            FlexEndTaskNodeConnection flexEndTaskNodeConnection =
                    context.getConnectionManager().getFirstConnection(FlexEndTaskNodeConnection.class, context, model);
            EndTaskNodesSet endTaskNodesSet =
                    flexEndTaskNodeConnection.getObjectWithRole(FlexEndTaskNodeConnection.ENDTASKNODES);
            for (SetFlex endSet : endTaskNodesSet)
                result.add(endSet);
        } catch (ConnectionCannotBeObtained e) {
            e.printStackTrace();
        }
        return result;
    }
}

