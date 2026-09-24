package org.fog.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SpringLayout;

import org.fog.gui.core.Edge;
import org.fog.gui.core.Graph;
import org.fog.gui.core.GraphSnapshot;
import org.fog.gui.core.Node;
import org.fog.gui.core.NodeCellRenderer;
import org.fog.gui.core.SpringUtilities;
import org.fog.gui.core.TopologyCommands;

/** A dialog to add a new edge */
public class AddAppEdge extends JDialog {
	private static final long serialVersionUID = 4794808969864918000L;

	private final transient TopologyCommands commands;
	private JComboBox<Node> sourceNode;
	private JComboBox<Node> targetNode;
	private JTextField tupleType;
	private JTextField tupleCpuLen;
	private JTextField tupleNwLen;

	// Swing construction necessarily invokes overridable JDialog hooks.
	@SuppressWarnings("this-escape")
	public AddAppEdge(final Graph graph, final JFrame frame) {

		this.commands = new TopologyCommands(graph);

		setLayout(new BorderLayout());

		add(createInputPanel(), BorderLayout.CENTER);
		add(createButtonPanel(), BorderLayout.PAGE_END);
		// show dialog
		setTitle("Add Application edge");
		setModal(true);
		setPreferredSize(new Dimension(400, 250));
		setResizable(false);
		pack();
		// must be called between pack and setVisible to work properly
		setLocationRelativeTo(frame);
		setVisible(true);
	}

	private JPanel createInputPanel() {
		final GraphSnapshot snapshot = commands.snapshot();

		Component rigid = Box.createRigidArea(new Dimension(10, 0));

		JPanel inputPanelWrapper = new JPanel();
		inputPanelWrapper.setLayout(new BoxLayout(inputPanelWrapper, BoxLayout.PAGE_AXIS));

		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.LINE_AXIS));

		JPanel textAreaPanel = new JPanel();
		textAreaPanel.setLayout(new BoxLayout(textAreaPanel, BoxLayout.LINE_AXIS));

		JPanel textAreaPanel2 = new JPanel();
		textAreaPanel2.setLayout(new BoxLayout(textAreaPanel2, BoxLayout.LINE_AXIS));

		ComboBoxModel<Node> sourceNodeModel = new DefaultComboBoxModel<Node>(
			snapshot.nodes().toArray(new Node[0]));

		sourceNodeModel.setSelectedItem(null);

		sourceNode = new JComboBox<Node>(sourceNodeModel);
		targetNode = new JComboBox<Node>();
		sourceNode.setMaximumSize(sourceNode.getPreferredSize());
		sourceNode.setMinimumSize(new Dimension(150, sourceNode.getPreferredSize().height));
		sourceNode.setPreferredSize(new Dimension(150, sourceNode.getPreferredSize().height));
		targetNode.setMaximumSize(targetNode.getPreferredSize());
		targetNode.setMinimumSize(new Dimension(150, targetNode.getPreferredSize().height));
		targetNode.setPreferredSize(new Dimension(150, targetNode.getPreferredSize().height));

		NodeCellRenderer renderer = new NodeCellRenderer();

		sourceNode.setRenderer(renderer);
		targetNode.setRenderer(renderer);

		sourceNode.addItemListener(new ItemListener() {

			@Override
			public void itemStateChanged(ItemEvent e) {
				// only display nodes which do not have already an edge

				targetNode.removeAllItems();
				Node selectedNode = (Node) sourceNode.getSelectedItem();

				if (selectedNode != null) {

					List<Node> nodesToDisplay = new ArrayList<Node>();
					Set<Node> allNodes = snapshot.nodes();

					// get edged for selected node and throw out all target
					// nodes where already an edge exists
					List<Edge> edgesForSelectedNode = snapshot.edgesFrom(selectedNode);
					Set<Node> nodesInEdges = new HashSet<Node>();
					for (Edge edge : edgesForSelectedNode) {
						nodesInEdges.add(edge.getNode());
					}

					for (Node node : allNodes) {
						if (!node.equals(selectedNode) && !nodesInEdges.contains(node)) {
							nodesToDisplay.add(node);
						}
					}

					ComboBoxModel<Node> targetNodeModel =
						new DefaultComboBoxModel<Node>(
							nodesToDisplay.toArray(new Node[0]));
					targetNode.setModel(targetNodeModel);
				}
			}
		});

		inputPanel.add(sourceNode);
		inputPanel.add(new Label("--->"));
		inputPanel.add(targetNode);
		inputPanel.add(Box.createHorizontalGlue());
		inputPanelWrapper.add(inputPanel);

		JPanel springPanel = new JPanel(new SpringLayout());

		JLabel tupleTypeLabel = new JLabel("Tuple Type : ");
		springPanel.add(tupleTypeLabel);
		tupleType = new JTextField();
		tupleTypeLabel.setLabelFor(tupleType);
		springPanel.add(tupleType);

		JLabel tupleCpuLenLabel = new JLabel("Tuple CPU Len : ");
		springPanel.add(tupleCpuLenLabel);
		tupleCpuLen = new JTextField();
		tupleCpuLenLabel.setLabelFor(tupleCpuLen);
		springPanel.add(tupleCpuLen);

		JLabel tupleNwLenLabel = new JLabel("Tuple NW Len : ");
		springPanel.add(tupleNwLenLabel);
		tupleNwLen = new JTextField();
		tupleNwLenLabel.setLabelFor(tupleNwLen);
		springPanel.add(tupleNwLen);

		SpringUtilities.makeCompactGrid(springPanel,
			3, 2,        // rows, cols
			6, 6,        // initX, initY
			6, 6);       // xPad, yPad

		inputPanelWrapper.add(springPanel);

		return inputPanelWrapper;
	}

	private JPanel createButtonPanel() {

		JPanel buttonPanel = new JPanel();
		buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));

		JButton okBtn = new JButton("Ok");
		JButton cancelBtn = new JButton("Cancel");

		cancelBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});

		okBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {

				String name = "default";
				double cpuLength = 0.0;
				double networkLength = 0.0;
				boolean catchedError = false;

				if (sourceNode.getSelectedItem() == null
					|| targetNode.getSelectedItem() == null) {
					catchedError = true;
					prompt("Please select node", "Error");
				} else if (tupleType.getText() == null
					|| tupleType.getText().trim().isEmpty()) {
					catchedError = true;
					prompt("Please enter Tuple Type", "Error");
				} else if (tupleCpuLen.getText() == null || tupleCpuLen.getText().isEmpty()) {
					catchedError = true;
					prompt("Please enter Tuple CPU Length", "Error");
				} else if (tupleNwLen.getText() == null || tupleNwLen.getText().isEmpty()) {
					catchedError = true;
					prompt("Please enter Tuple NW Length", "Error");
				}
				else {
					try {
						cpuLength = Double.parseDouble(tupleCpuLen.getText());
						networkLength = Double.parseDouble(tupleNwLen.getText());
						if (!Double.isFinite(cpuLength) || cpuLength < 0.0
							|| !Double.isFinite(networkLength)
							|| networkLength < 0.0) {
							throw new NumberFormatException();
						}
					}
					catch (NumberFormatException error) {
						catchedError = true;
						prompt("Tuple lengths must be finite, non-negative numbers",
							"Error");
					}
				}

				if (!catchedError) {
					Node source = (Node) sourceNode.getSelectedItem();
					Node target = (Node) targetNode.getSelectedItem();
					name = source.getName() + "-" + target.getName();
					Edge edge = new Edge(target, name,
						tupleType.getText().trim(), cpuLength, networkLength);
					commands.addEdge(source, edge);
					setVisible(false);
				}

			}
		});

		buttonPanel.add(Box.createHorizontalGlue());
		buttonPanel.add(okBtn);
		buttonPanel.add(Box.createRigidArea(new Dimension(10, 0)));
		buttonPanel.add(cancelBtn);
		buttonPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		return buttonPanel;
	}

	private void prompt(String msg, String type) {
		JOptionPane.showMessageDialog(AddAppEdge.this, msg, type, JOptionPane.ERROR_MESSAGE);
	}

}
