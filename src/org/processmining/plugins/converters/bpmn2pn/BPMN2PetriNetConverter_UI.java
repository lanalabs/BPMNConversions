package org.processmining.plugins.converters.bpmn2pn;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.JLabel;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.util.ui.widgets.LeftAlignedHeader;
import org.processmining.plugins.converters.bpmn2pn.BPMN2PetriNetConverter_Configuration.EndEventJoin;
import org.processmining.plugins.converters.bpmn2pn.ui.ProMPropertiesPanel;

public class BPMN2PetriNetConverter_UI extends ProMPropertiesPanel {

	private static final long serialVersionUID = 1L;
	
	private javax.swing.JCheckBox linkSubProcessToActivityBox;
	private javax.swing.JCheckBox translateWithLifeCycleVisible;
	private javax.swing.JComboBox<String> labelType;
	private javax.swing.JComboBox<BPMN2PetriNetConverter_Configuration.EndEventJoin> endEventJoin;
	private javax.swing.JCheckBox labelFlowPlaces;
	private javax.swing.JCheckBox makeRoutingTransitionsVisible;
	private javax.swing.JCheckBox makeStartEndEventsVisible;
	private javax.swing.JCheckBox makeIntermediateEventsVisible;
	private javax.swing.JComboBox<String> analysisType;
			
	private static final String DIALOG_NAME = "Options for translation to Petri net";
	
	private static final String translationTypes[] = { "Soundness checking", "Conformance checking" };
	private static final int translationType_SOUNDNESS = 0;
	private static final int translationType_CONFORMANCE = 1;
	
	private ArrayList<Component> soundessGroup = new ArrayList<Component>();
	private ArrayList<Component> conformanceGroup = new ArrayList<Component>();

	
	public BPMN2PetriNetConverter_UI(BPMN2PetriNetConverter_Configuration config) {
		
		super(null);
		
		addToProperties(new LeftAlignedHeader(DIALOG_NAME));
		addToProperties(Box.createVerticalStrut(15));
		
		analysisType = addComboBox("Translate for", translationTypes, 0, 400);
		analysisType.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				updatePanelOptions(analysisType.getSelectedIndex());
			}
		});
		addToProperties(Box.createVerticalStrut(15));
		
		JLabel behaviorOptions_subProcess = new JLabel("How to translate subprocesses?");
		behaviorOptions_subProcess.setAlignmentX(Component.RIGHT_ALIGNMENT);
		behaviorOptions_subProcess.setMaximumSize(new Dimension(1000, 20));
		addToProperties(behaviorOptions_subProcess);
		linkSubProcessToActivityBox = addCheckBox("Replace subprocess activity by subprocess definition", config.linkSubProcessToActivity, 1, 400);
		Component vStrut_subProcess = Box.createVerticalStrut(15);
		addToProperties(vStrut_subProcess);
			soundessGroup.add(behaviorOptions_subProcess);
			soundessGroup.add(linkSubProcessToActivityBox.getParent());
			soundessGroup.add(vStrut_subProcess);
		
		JLabel behaviorOptions_finalNodes = new JLabel("How to translate termination semantics?");
		behaviorOptions_finalNodes.setAlignmentX(Component.RIGHT_ALIGNMENT);
		behaviorOptions_finalNodes.setMaximumSize(new Dimension(1000, 20));
		addToProperties(behaviorOptions_finalNodes);
		endEventJoin = addComboBox("Treat multiple incoming sequence flows of end events as", BPMN2PetriNetConverter_Configuration.EndEventJoin.values(), 1, 400);
		endEventJoin.setSelectedIndex(config.endEventJoin.ordinal());
		Component vStrut_finalNodes = Box.createVerticalStrut(15);
		addToProperties(vStrut_finalNodes);
			soundessGroup.add(behaviorOptions_finalNodes);
			soundessGroup.add(endEventJoin.getParent());
			soundessGroup.add(vStrut_finalNodes);
			conformanceGroup.add(behaviorOptions_finalNodes);
			conformanceGroup.add(endEventJoin.getParent());
			conformanceGroup.add(vStrut_finalNodes);
		
		JLabel visibilityLabel = new JLabel("Which transitions shall be visible?");
		visibilityLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		visibilityLabel.setMaximumSize(new Dimension(1000, 20));
		addToProperties(visibilityLabel);
		translateWithLifeCycleVisible = addCheckBox("Start/complete life-cycle is visible", config.translateWithLifeCycleVisible, 1, 400);
		makeRoutingTransitionsVisible = addCheckBox("Routing constructs are visible", config.makeRoutingTransitionsVisible, 1, 400);
		makeStartEndEventsVisible = addCheckBox("Start and end events are visible", config.makeStartEndEventsVisible, 1, 400);
		makeIntermediateEventsVisible = addCheckBox("Intermediate events are visible", config.makeIntermediateEventsVisible, 1, 400);
		Component vStrut_visiblity = Box.createVerticalStrut(15);
		addToProperties(vStrut_visiblity);
//			conformanceGroup.add(visibilityLabel);
//			conformanceGroup.add(translateWithLifeCycleVisible.getParent());
//			conformanceGroup.add(makeRoutingTransitionsVisible.getParent());
//			conformanceGroup.add(makeStartEndEventsVisible.getParent());
//			conformanceGroup.add(makeIntermediateEventsVisible.getParent());
//			conformanceGroup.add(vStrut_visiblity);
		
		JLabel namingLabel = new JLabel("How shall nodes be labeled?");
		namingLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
		namingLabel.setMaximumSize(new Dimension(1000, 20));
		addToProperties(namingLabel);
		String[] labelValues = {
				"just original label", //ORIGINAL_LABEL,
				"prefix routing constructs with BPMN type", //PREFIX_NONTASK_BY_BPMN_TYPE,
				"prefix all constructs with BPMN type", //PREFIX_ALL_BY_BPMN_TYPE,
				"prefix all constructs with Petri net and BPMN type", //PREFIX_ALL_BY_PN_BPMN_TYPE
		};
		labelType = addComboBox("General node label", labelValues, 1, 400);
		labelType.setSelectedIndex(config.labelNodesWith.ordinal());
		labelFlowPlaces = addCheckBox("Generate labels for places for sequence flows (if no label defined)", config.labelFlowPlaces, 1, 400);
		
		updatePanelOptions(analysisType.getSelectedIndex());
	}
	
	private void updatePanelOptions(int analysisType) {
		switch (analysisType) {
			case translationType_SOUNDNESS:
				for (Component c : conformanceGroup) c.setVisible(false);
				for (Component c : soundessGroup) c.setVisible(true);
				linkSubProcessToActivityBox.setSelected(false);
				makeStartEndEventsVisible.setSelected(true);
				makeIntermediateEventsVisible.setSelected(true);
				makeRoutingTransitionsVisible.setSelected(false);
				translateWithLifeCycleVisible.setSelected(true);
				BPMN2PetriNetConverter_UI.this.repaint();
				break;
			case translationType_CONFORMANCE:
				for (Component c : soundessGroup) c.setVisible(false);
				for (Component c : conformanceGroup) c.setVisible(true);
				linkSubProcessToActivityBox.setSelected(true);
				makeStartEndEventsVisible.setSelected(false);
				makeIntermediateEventsVisible.setSelected(false);
				makeRoutingTransitionsVisible.setSelected(false);
				translateWithLifeCycleVisible.setSelected(false);
				BPMN2PetriNetConverter_UI.this.repaint();
				break;
			default:
				for (Component c : soundessGroup) c.setVisible(true);
				for (Component c : conformanceGroup) c.setVisible(true);
				BPMN2PetriNetConverter_UI.this.repaint();
				break;
		}
	}
	
	/**
	 * Open UI dialogue to populate the given configuration object with
	 * settings chosen by the user.
	 * 
	 * @param context
	 * @param config
	 * @return result of the user interaction
	 */
	public InteractionResult setParameters(UIPluginContext context, BPMN2PetriNetConverter_Configuration config) {
		InteractionResult wish = getUserChoice(context);
		if (wish != InteractionResult.CANCEL) getChosenParameters(config);
		return wish;
	}
	
	/**
	 * @return Configuration as picked in the user interface, call only after
	 *         {@link #getUserChoice(UIPluginContext)} was called
	 */
	private void getChosenParameters(BPMN2PetriNetConverter_Configuration config) {
		config.translateWithLifeCycleVisible = translateWithLifeCycleVisible.isSelected();
		config.linkSubProcessToActivity = linkSubProcessToActivityBox.isSelected();
		config.labelNodesWith = BPMN2PetriNetConverter_Configuration.LabelValue.values()[labelType.getSelectedIndex()];
		config.labelFlowPlaces = labelFlowPlaces.isSelected();
		config.makeRoutingTransitionsVisible = makeRoutingTransitionsVisible.isSelected();
		config.makeStartEndEventsVisible = makeStartEndEventsVisible.isSelected();
		config.makeIntermediateEventsVisible = makeIntermediateEventsVisible.isSelected();
		config.endEventJoin = (EndEventJoin) endEventJoin.getSelectedItem();
	}
	
	/**
	 * display a dialog to ask user what to do
	 * 
	 * @param context
	 * @return
	 */
	protected InteractionResult getUserChoice(UIPluginContext context) {
		return context.showConfiguration("Translate BPMN to Petri net", this);
	}
	
	/**
	 * Generate proper cancelling information for Uma. 
	 * @param context
	 * @return
	 */
	protected Object[] userCancel(PluginContext context) {
		return BPMN2PetriNetConverter_Plugin.cancel(context, "The user has cancelled BPMN2PetriNetConverter.");
	}

}
