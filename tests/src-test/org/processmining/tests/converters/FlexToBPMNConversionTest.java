package org.processmining.tests.converters;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;

import org.junit.Test;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.connections.ConnectionCannotBeObtained;
import org.processmining.framework.connections.ConnectionManager;
import org.processmining.models.connections.flexiblemodel.FlexEndTaskNodeConnection;
import org.processmining.models.connections.flexiblemodel.FlexStartTaskNodeConnection;
import org.processmining.models.flexiblemodel.EndTaskNodesSet;
import org.processmining.models.flexiblemodel.Flex;
import org.processmining.models.flexiblemodel.FlexImpl;
import org.processmining.models.flexiblemodel.FlexNode;
import org.processmining.models.flexiblemodel.SetFlex;
import org.processmining.models.flexiblemodel.StartTaskNodesSet;
import org.processmining.plugins.converters.FlexToBPMNConversionPlugin;

public class FlexToBPMNConversionTest {

    @Test
    public void converter_withSimpleCNet_expectedNoFailures()
            throws ConnectionCannotBeObtained {
        Flex causalNet = new FlexImpl("label");

        // nodes
        FlexNode a = causalNet.addNode("a");
        FlexNode b = causalNet.addNode("b");
        FlexNode c = causalNet.addNode("c");
        FlexNode d = causalNet.addNode("d");

        // arcs
        causalNet.addArc(a, b);
        causalNet.addArc(a, c);
        causalNet.addArc(b, d);
        causalNet.addArc(c, d);

        // outputs for a
        SetFlex aOut1 = new SetFlex();
        aOut1.add(b);
        SetFlex aOut2 = new SetFlex();
        aOut2.add(c);
        aOut2.add(b);
        a.addOutputNodes(aOut1);
        a.addOutputNodes(aOut2);

        // inputs for b
        SetFlex bIn = new SetFlex();
        bIn.add(a);
        b.addInputNodes(bIn);

        // outputs for b
        SetFlex bOut = new SetFlex();
        bOut.add(d);
        b.addOutputNodes(bOut);

        // inputs for c
        SetFlex cIn = new SetFlex();
        cIn.add(a);
        c.addInputNodes(cIn);

        // outputs for c
        SetFlex cOut = new SetFlex();
        cOut.add(d);
        c.addOutputNodes(cOut);

        // inputs for d
        SetFlex dIn1 = new SetFlex();
        dIn1.add(b);
        SetFlex dIn2 = new SetFlex();
        dIn2.add(b);
        dIn2.add(c);
        d.addInputNodes(dIn1);
        d.addInputNodes(dIn2);


        UIPluginContext mockedContext = mock(UIPluginContext.class);

        StartTaskNodesSet startTaskSet = new StartTaskNodesSet();
        SetFlex startSet = new SetFlex();
        startSet.add(a);
        startTaskSet.add(startSet);
        FlexStartTaskNodeConnection mockedFlexStartConnection = mock(FlexStartTaskNodeConnection.class);
        when(mockedFlexStartConnection.getObjectWithRole(FlexStartTaskNodeConnection.STARTTASKNODES))
                .thenReturn(startTaskSet);

        EndTaskNodesSet endTaskSet = new EndTaskNodesSet();
        SetFlex endSet = new SetFlex();
        endSet.add(d);
        endTaskSet.add(endSet);
        FlexEndTaskNodeConnection mockedFlexEndConnection = mock(FlexEndTaskNodeConnection.class);
        when(mockedFlexEndConnection.getObjectWithRole(FlexEndTaskNodeConnection.ENDTASKNODES))
                .thenReturn(endTaskSet);

        ConnectionManager mockedManager = mock(ConnectionManager.class);
        when(mockedManager.getFirstConnection(FlexStartTaskNodeConnection.class, mockedContext, causalNet))
                .thenReturn(mockedFlexStartConnection);
        when(mockedManager.getFirstConnection(FlexEndTaskNodeConnection.class, mockedContext, causalNet))
                .thenReturn(mockedFlexEndConnection);

        when(mockedContext.getConnectionManager()).thenReturn(mockedManager);

        new FlexToBPMNConversionPlugin().converter(mockedContext, causalNet);
    }

    @Test
    public void converter_withConnectionCannotBeObtained_expectedNoFailures()
            throws ConnectionCannotBeObtained {
        Flex causalNet = new FlexImpl("label");

        // nodes
        FlexNode a = causalNet.addNode("a");
        FlexNode b = causalNet.addNode("b");
        FlexNode c = causalNet.addNode("c");
        FlexNode d = causalNet.addNode("d");

        // arcs
        causalNet.addArc(a, b);
        causalNet.addArc(a, c);
        causalNet.addArc(b, d);
        causalNet.addArc(c, d);

        // outputs for a
        SetFlex aOut1 = new SetFlex();
        aOut1.add(b);
        SetFlex aOut2 = new SetFlex();
        aOut2.add(c);
        aOut2.add(b);
        a.addOutputNodes(aOut1);
        a.addOutputNodes(aOut2);

        // inputs for b
        SetFlex bIn = new SetFlex();
        bIn.add(a);
        b.addInputNodes(bIn);

        // outputs for b
        SetFlex bOut = new SetFlex();
        bOut.add(d);
        b.addOutputNodes(bOut);

        // inputs for c
        SetFlex cIn = new SetFlex();
        cIn.add(a);
        c.addInputNodes(cIn);

        // outputs for c
        SetFlex cOut = new SetFlex();
        cOut.add(d);
        c.addOutputNodes(cOut);

        // inputs for d
        SetFlex dIn1 = new SetFlex();
        dIn1.add(b);
        SetFlex dIn2 = new SetFlex();
        dIn2.add(b);
        dIn2.add(c);
        d.addInputNodes(dIn1);
        d.addInputNodes(dIn2);

        UIPluginContext mockedContext = mock(UIPluginContext.class);

        ConnectionManager mockedManager = mock(ConnectionManager.class);
        when(mockedManager.getFirstConnection(FlexStartTaskNodeConnection.class, mockedContext, causalNet))
                .thenThrow(ConnectionCannotBeObtained.class);
        when(mockedManager.getFirstConnection(FlexEndTaskNodeConnection.class, mockedContext, causalNet))
                .thenThrow(ConnectionCannotBeObtained.class);

        when(mockedContext.getConnectionManager()).thenReturn(mockedManager);

        new FlexToBPMNConversionPlugin().converter(mockedContext, causalNet);
    }

    @Test
    public void converter_withMultipleInputsAndOutputs_expectedNoFailures()
            throws ConnectionCannotBeObtained {
        Flex causalNet = new FlexImpl("label");

        FlexNode a = causalNet.addNode("a");
        FlexNode b = causalNet.addNode("b");
        FlexNode c = causalNet.addNode("c");
        FlexNode d = causalNet.addNode("d");
        FlexNode e = causalNet.addNode("e");
        FlexNode f = causalNet.addNode("f");
        FlexNode g = causalNet.addNode("g");

        a.addOutputNodes(setFlex(c));
        b.addOutputNodes(setFlex(d));
        c.addInputNodes(setFlex(a));
        c.addOutputNodes(setFlex(e));
        d.addInputNodes(setFlex(b));
        d.addOutputNodes(setFlex(e));
        e.addInputNodes(setFlex(c));
        e.addInputNodes(setFlex(d));
        e.addOutputNodes(setFlex(f));
        e.addOutputNodes(setFlex(g));
        f.addInputNodes(setFlex(e));
        g.addInputNodes(setFlex(e));

        causalNet.addArc(a, c);
        causalNet.addArc(b, d);
        causalNet.addArc(c, e);
        causalNet.addArc(d, e);
        causalNet.addArc(e, f);
        causalNet.addArc(e, g);

        UIPluginContext mockedContext = mock(UIPluginContext.class);

        StartTaskNodesSet startTaskSet = new StartTaskNodesSet();
        startTaskSet.add(setFlex(a));
        startTaskSet.add(setFlex(b));
        FlexStartTaskNodeConnection mockedFlexStartConnection = mock(FlexStartTaskNodeConnection.class);
        when(mockedFlexStartConnection.getObjectWithRole(FlexStartTaskNodeConnection.STARTTASKNODES))
                .thenReturn(startTaskSet);

        EndTaskNodesSet endTaskSet = new EndTaskNodesSet();
        endTaskSet.add(setFlex(f));
        endTaskSet.add(setFlex(g));

        FlexEndTaskNodeConnection mockedFlexEndConnection = mock(FlexEndTaskNodeConnection.class);
        when(mockedFlexEndConnection.getObjectWithRole(FlexEndTaskNodeConnection.ENDTASKNODES))
                .thenReturn(endTaskSet);

        ConnectionManager mockedManager = mock(ConnectionManager.class);
        when(mockedManager.getFirstConnection(FlexStartTaskNodeConnection.class, mockedContext, causalNet))
                .thenReturn(mockedFlexStartConnection);
        when(mockedManager.getFirstConnection(FlexEndTaskNodeConnection.class, mockedContext, causalNet))
                .thenReturn(mockedFlexEndConnection);

        when(mockedContext.getConnectionManager()).thenReturn(mockedManager);

        new FlexToBPMNConversionPlugin().converter(mockedContext, causalNet);

    }

    private SetFlex setFlex(FlexNode... nodes) {
        assert nodes != null;
        SetFlex result = new SetFlex();
        Collections.addAll(result, nodes);
        return result;
    }
}
