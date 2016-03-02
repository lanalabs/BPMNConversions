package org.processmining.plugins.converters.bpmn2pn;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.Box;
import javax.swing.JLabel;

import org.deckfour.uitopia.api.event.TaskListener.InteractionResult;
import org.processmining.contexts.uitopia.UIPluginContext;
import org.processmining.framework.plugin.PluginContext;
import org.processmining.framework.util.ui.widgets.LeftAlignedHeader;
import org.processmining.plugins.converters.bpmn2pn.ui.ProMPropertiesPanel;

public class BPMN2PetriNetConverter_UI extends ProMPropertiesPanel {

	private static final long serialVersionUID = 1L;
	
	private javax.swing.JCheckBox linkSubProcessToActivityBox;
	
	private static final String DIALOG_NAME = "Options for translation to Petri net";
	
	public BPMN2PetriNetConverter_UI(BPMN2PetriNetConverter_Configuration config) {
		
		super(null);
		
		addToProperties(new LeftAlignedHeader(DIALOG_NAME));
		addToProperties(Box.createVerticalStrut(15));
		JLabel main_description = new JLabel();
		main_description.setAlignmentX(Component.RIGHT_ALIGNMENT);
		main_description.setText("The conversion to Petri nets can be configured.");
		main_description.setOpaque(false);
		main_description.setFont(main_description.getFont().deriveFont(12f));
		main_description.setFont(main_description.getFont().deriveFont(Font.PLAIN));
		main_description.setMinimumSize(new Dimension(1000, 20));
		main_description.setMaximumSize(new Dimension(1000, 1000));
		main_description.setPreferredSize(new Dimension(1000, 30));
		addToProperties(main_description);
		addToProperties(Box.createVerticalStrut(15));

		
		linkSubProcessToActivityBox = addCheckBox("Link subprocess definition to its calling activity", config.linkSubProcessToActivity, 0, 400);
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
		config.linkSubProcessToActivity = linkSubProcessToActivityBox.isSelected();
	}
	
	/**
	 * display a dialog to ask user what to do
	 * 
	 * @param context
	 * @return
	 */
	protected InteractionResult getUserChoice(UIPluginContext context) {
		return context.showConfiguration("Repair Model", this);
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
