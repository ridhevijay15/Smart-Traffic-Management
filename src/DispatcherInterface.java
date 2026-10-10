import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class DispatcherInterface {

    private static final AtomicInteger ARRIVAL_COUNTER = new AtomicInteger();

    private DispatcherInterface() {
    }

    public static void runConsole(TrafficController controller, Scanner scanner) {
        System.out.println("\n====== VEGAMTRAFFIC DISPATCHER ======");
        String vehicleType = readChoice(scanner, "Choose vehicle type:",
                DispatcherRequestFactory.AMBULANCE,
                DispatcherRequestFactory.FIRE_TRUCK,
                DispatcherRequestFactory.POLICE);
        List<String> situations = DispatcherRequestFactory.getSituations(vehicleType);
        String situation = readChoice(scanner, "Choose situation:",
                situations.toArray(new String[0]));
        String source = readJunction(scanner, controller, "Enter origin junction: ");
        String destination = readJunction(scanner, controller, "Enter destination junction: ");
        System.out.print("Enter incident description: ");
        String description = scanner.nextLine().trim();

        try {
            RoutingRequest request = createRequest(
                    controller, vehicleType, situation, source, destination, description);
            EmergencyVehicle vehicle = DispatcherRequestFactory.findVehicle(
                    controller, vehicleType);
            controller.setVehiclePosition(vehicle.getVehicleId(), source);
            if (controller.addRoutingRequest(request)) {
                System.out.println("\nDispatcher created RoutingRequest:");
                System.out.println(request);
            }
        } catch (IllegalArgumentException exception) {
            System.out.println("Could not create routing request: " + exception.getMessage());
        }
    }

    public static void showGui(TrafficController controller) {
        SwingUtilities.invokeLater(() -> createWindow(controller));
    }

    public static RoutingRequest createRequest(TrafficController controller,
                                               String vehicleType,
                                               String situation,
                                               String source,
                                               String destination,
                                               String description) {
        int arrivalOrder = ARRIVAL_COUNTER.incrementAndGet();
        String requestId = "DISP-REQ-" + arrivalOrder;
        return DispatcherRequestFactory.createRequest(controller, requestId,
                arrivalOrder, vehicleType, situation, source, destination, description);
    }

    private static void createWindow(TrafficController controller) {
        JFrame frame = new JFrame("VegamTraffic | Emergency Dispatcher");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(650, 570));

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(26, 34, 26, 34));
        content.setBackground(new Color(246, 248, 249));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 6, 7, 6);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;

        JLabel title = new JLabel("VEGAMTRAFFIC DISPATCHER");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        title.setForeground(new Color(20, 55, 70));
        addRow(content, constraints, 0, title, 2);

        JLabel subtitle = new JLabel("Create a prioritized emergency routing request");
        subtitle.setForeground(new Color(100, 119, 130));
        addRow(content, constraints, 1, subtitle, 2);

        JComboBox<String> vehicleTypes = new JComboBox<>(new String[] {
                DispatcherRequestFactory.AMBULANCE,
                DispatcherRequestFactory.FIRE_TRUCK,
                DispatcherRequestFactory.POLICE
        });
        JComboBox<String> situations = new JComboBox<>();
        JComboBox<String> origins = new JComboBox<>(
                controller.getRoadNetwork().getJunctions().toArray(new String[0]));
        JComboBox<String> destinations = new JComboBox<>(
                controller.getRoadNetwork().getJunctions().toArray(new String[0]));
        JLabel severityValue = new JLabel();
        severityValue.setFont(new Font("SansSerif", Font.BOLD, 14));
        JTextArea description = new JTextArea(4, 25);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));

        addLabeledRow(content, constraints, 2, "Vehicle type", vehicleTypes);
        addLabeledRow(content, constraints, 3, "Situation", situations);
        addLabeledRow(content, constraints, 4, "Automatic severity", severityValue);
        addLabeledRow(content, constraints, 5, "Origin junction", origins);
        addLabeledRow(content, constraints, 6, "Destination junction", destinations);
        addLabeledRow(content, constraints, 7, "Incident description",
                new JScrollPane(description));

        JLabel assignedVehicle = new JLabel();
        assignedVehicle.setForeground(new Color(90, 108, 119));
        addRow(content, constraints, 8, assignedVehicle, 2);

        JButton submit = new JButton("Create routing request");
        submit.setBackground(new Color(22, 142, 109));
        submit.setForeground(Color.WHITE);
        submit.setFocusPainted(false);
        addRow(content, constraints, 9, submit, 2);

        vehicleTypes.addActionListener((ActionEvent event) ->
                updateSituations((String) vehicleTypes.getSelectedItem(),
                        situations, severityValue, assignedVehicle, origins, controller));
        situations.addActionListener((ActionEvent event) ->
                updateSeverity((String) vehicleTypes.getSelectedItem(),
                        (String) situations.getSelectedItem(), severityValue));
        submit.addActionListener((ActionEvent event) -> {
            String vehicleType = (String) vehicleTypes.getSelectedItem();
            String situation = (String) situations.getSelectedItem();
            String source = (String) origins.getSelectedItem();
            String destination = (String) destinations.getSelectedItem();
            try {
                RoutingRequest request = createRequest(controller, vehicleType,
                        situation, source, destination, description.getText());
                EmergencyVehicle vehicle = DispatcherRequestFactory.findVehicle(
                        controller, vehicleType);
                controller.setVehiclePosition(vehicle.getVehicleId(), source);
                if (controller.addRoutingRequest(request)) {
                    JOptionPane.showMessageDialog(frame,
                            "Routing request added:\n" + request,
                            "Request created", JOptionPane.INFORMATION_MESSAGE);
                    description.setText("");
                } else {
                    JOptionPane.showMessageDialog(frame,
                            "The traffic controller rejected the request.",
                            "Request not added", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(frame, exception.getMessage(),
                        "Check dispatcher details", JOptionPane.WARNING_MESSAGE);
            }
        });

        updateSituations((String) vehicleTypes.getSelectedItem(),
                situations, severityValue, assignedVehicle, origins, controller);

        frame.setContentPane(content);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void updateSituations(String vehicleType,
                                         JComboBox<String> situations,
                                         JLabel severityValue,
                                         JLabel assignedVehicle,
                                         JComboBox<String> origins,
                                         TrafficController controller) {
        situations.removeAllItems();
        for (String situation : DispatcherRequestFactory.getSituations(vehicleType)) {
            situations.addItem(situation);
        }
        updateSeverity(vehicleType, (String) situations.getSelectedItem(), severityValue);
        EmergencyVehicle vehicle = DispatcherRequestFactory.findVehicle(
                controller, vehicleType);
        if (vehicle == null) {
            assignedVehicle.setText("No available vehicle of this type is registered.");
        } else {
            assignedVehicle.setText("Assigned unit: " + vehicle.getVehicleId()
                    + " · Current location: " + vehicle.getCurrentJunction());
            origins.setSelectedItem(vehicle.getCurrentJunction());
        }
    }

    private static void updateSeverity(String vehicleType, String situation,
                                       JLabel severityValue) {
        if (vehicleType != null && situation != null) {
            severityValue.setText(DispatcherRequestFactory.getSeverity(
                    vehicleType, situation).toUpperCase());
        }
    }

    private static void addLabeledRow(JPanel panel, GridBagConstraints constraints,
                                      int row, String label, java.awt.Component field) {
        JLabel caption = new JLabel(label);
        caption.setForeground(new Color(64, 82, 94));
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0.35;
        panel.add(caption, constraints);
        constraints.gridx = 1;
        constraints.weightx = 0.65;
        panel.add(field, constraints);
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints,
                               int row, java.awt.Component component, int span) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = span;
        constraints.weightx = 1;
        panel.add(component, constraints);
        constraints.gridwidth = 1;
    }

    private static String readChoice(Scanner scanner, String prompt,
                                     String... choices) {
        while (true) {
            System.out.println(prompt);
            for (int i = 0; i < choices.length; i++) {
                System.out.println((i + 1) + ". " + choices[i]);
            }
            System.out.print("Enter choice: ");
            String value = scanner.nextLine().trim();
            try {
                int index = Integer.parseInt(value) - 1;
                if (index >= 0 && index < choices.length) {
                    return choices[index];
                }
            } catch (NumberFormatException exception) {
                for (String choice : choices) {
                    if (choice.equalsIgnoreCase(value)) {
                        return choice;
                    }
                }
            }
            System.out.println("Invalid choice. Please try again.");
        }
    }

    private static String readJunction(Scanner scanner,
                                       TrafficController controller,
                                       String prompt) {
        while (true) {
            System.out.print(prompt);
            String junction = scanner.nextLine().trim().toUpperCase();
            if (controller.getRoadNetwork().hasJunction(junction)) {
                return junction;
            }
            System.out.println("Junction not found. Choose from: "
                    + controller.getRoadNetwork().getJunctions());
        }
    }
}
