package org.fog.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.concurrent.ExecutionException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import org.cloudbus.cloudsim.sdn.graph.example.GraphicSDNExample;

public class SDNRun extends JDialog {
	private static final long serialVersionUID = -8313194085507492462L;

	private String physicalTopologyFile = "";  // physical
	private String deploymentFile = "";        // virtual
	private String workloads_background = "";  // workload
	private String workloads = "";             // workload

	private JPanel panel;
	private JScrollPane pane;
	private JTextArea outputArea;
	private JLabel imageLabel;
	private JLabel msgLabel;
	private JComponent space;
	private transient GraphicSDNExample sdn;

	// Swing construction necessarily invokes overridable JDialog hooks.
	@SuppressWarnings("this-escape")
	public SDNRun(final String phy, final String vir, final String wlbk, final String wl,
		final JFrame frame) {
		physicalTopologyFile = phy;
		deploymentFile = vir;
		workloads_background = wlbk;
		workloads = wl;

		setLayout(new BorderLayout());

		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

		initUI();
		run();
		add(panel, BorderLayout.CENTER);

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setTitle("Run Simulation");
		setModal(true);
		setPreferredSize(new Dimension(900, 600));
		setResizable(false);
		pack();
		// must be called between pack and setVisible to work properly
		setLocationRelativeTo(frame);
		setVisible(true);
	}

	private void initUI() {
		ImageIcon ii = new ImageIcon(this.getClass().getResource("/images/1.gif"));
		imageLabel = new JLabel(ii);
		imageLabel.setAlignmentX(CENTER_ALIGNMENT);
		msgLabel = new JLabel("Simulation is executing");
		msgLabel.setAlignmentX(CENTER_ALIGNMENT);
		space = (JComponent) Box.createRigidArea(new Dimension(0, 200));
		panel.add(space);
		panel.add(msgLabel);
		panel.add(imageLabel);

		pane = new JScrollPane();
		outputArea = new JTextArea();

		outputArea.setLineWrap(true);
		outputArea.setWrapStyleWord(true);
		outputArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		outputArea.setEditable(false);
		pane.getViewport().add(outputArea);
		panel.add(pane);
		pane.setVisible(false);
	}

	private void run() {

		sdn = new GraphicSDNExample(physicalTopologyFile, deploymentFile, workloads_background,
			workloads, outputArea);

		SwingWorker<Boolean, Void> worker = new SwingWorker<Boolean, Void>() {
			@Override
			protected Boolean doInBackground() throws Exception {
				return sdn.simulate();
			}

			@Override
			protected void done() {
				try {
					if (get()) {
						sdn.output();
						append("<<<<<<<<<< Simulation completed >>>>>>>>>");
					}
				}
				catch (InterruptedException error) {
					Thread.currentThread().interrupt();
					showFailure("Simulation was interrupted", error);
				}
				catch (ExecutionException error) {
					showFailure("Simulation failed", rootCause(error));
				}
				catch (RuntimeException error) {
					showFailure("Could not display simulation results", error);
				}
				finally {
					showOutput();
				}
			}
		};
		worker.execute();
	}

	private void append(String content) {
		outputArea.append(content + "\n");
	}

	private void showOutput() {
		panel.remove(space);
		panel.remove(imageLabel);
		panel.remove(msgLabel);
		pane.setVisible(true);
		panel.revalidate();
		panel.repaint();
	}

	private void showFailure(String summary, Throwable error) {
		String detail = error == null || error.getMessage() == null
			? "No further details are available." : error.getMessage();
		append("<<<<<<<<<< " + summary + ": " + detail + " >>>>>>>>>>");
		JOptionPane.showMessageDialog(this, summary + ":\n" + detail,
			"Simulation error", JOptionPane.ERROR_MESSAGE);
	}

	private static Throwable rootCause(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		return cause;
	}

}
