import java.awt.GridLayout;
import java.util.List;
import java.util.Scanner;
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

    private static int arrivalCounter = 0;

    private DispatcherInterface() {
    }

    public static void runConsole(TrafficController controller, Scanner scanner) {
        System.out.println("\n====== VEGAMTRAFFIC DISPATCHER ======");

        String vehicleType = readVehicleType(scanner);
        List<String> situations = DispatcherRequestFactory.getSituations(vehicleType);
        String situation = readChoice(scanner, "Choose situation:", situations);
        String source = readJunction(scanner, controller, "Enter origin junction: ");
        String destination = readJunction(scanner, controller, "Enter destination junction: ");
        System.out.print("Enter incident description: ");
        String description = scanner.nextLine();

        try {
            addRequest(controller, vehicleType, situation, source, destination, description);
        } catch (IllegalArgumentException exception) {
            System.out.println("Could not create routing request: " + exception.getMessage());
        }
    }

    private static String readVehicleType(Scanner scanner) {
        System.out.println("Choose vehicle type:");
        System.out.println("1. AMBULANCE");
        System.out.println("2. FIRE_TRUCK");
        System.out.println("3. POLICE");

        int choice = readNumber(scanner, "Enter choice: ", 1, 3);
        switch (choice) {
            case 1:
                return DispatcherRequestFactory.AMBULANCE;
            case 2:
                return DispatcherRequestFactory.FIRE_TRUCK;
            default:
                return DispatcherRequestFactory.POLICE;
        }
    }

    private static String readChoice(Scanner scanner, String prompt, List<String> choices) {
        System.out.println(prompt);
        for (int i = 0; i < choices.size(); i++) {
            System.out.println((i + 1) + ". " + choices.get(i));
        }
        int choice = readNumber(scanner, "Enter choice: ", 1, choices.size());
        return choices.get(choice - 1);
    }

    private static int readNumber(Scanner scanner, String prompt, int minimum, int maximum) {
        while (true) {
            System.out.print(prompt);
            try {
                int choice = Integer.parseInt(scanner.nextLine());
                if (choice >= minimum && choice <= maximum) {
                    return choice;
                }
            } catch (NumberFormatException exception) {
                // Ask again when the input is not a valid number.
            }
            System.out.println("Enter a number from " + minimum + " to " + maximum + ".");
        }
    }

    private static String readJunction(Scanner scanner, TrafficController controller,
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

    public static void showGui(TrafficController controller) {
        SwingUtilities.invokeLater(() -> createWindow(controller));
    }

    public static RoutingRequest createRequest(TrafficController controller,
                                               String vehicleType,
                                               String situation,
                                               String source,
                                               String destination,
                                               String description) {
        arrivalCounter++;
        String requestId = "DISP-REQ-" + arrivalCounter;
        return DispatcherRequestFactory.createRequest(controller, requestId,
                arrivalCounter, vehicleType, situation, source, destination, description);
    }

    private static void addRequest(TrafficController controller, String vehicleType,
                                   String situation, String source, String destination,
                                   String description) {
        RoutingRequest request = createRequest(controller, vehicleType, situation,
                source, destination, description);
        EmergencyVehicle vehicle = DispatcherRequestFactory.findVehicle(
                controller, vehicleType);
        controller.setVehiclePosition(vehicle.getVehicleId(), source);
        if (controller.addRoutingRequest(request)) {
            System.out.println("\nDispatcher created RoutingRequest:");
            System.out.println(request);
        } else {
            throw new IllegalArgumentException("The traffic controller rejected the request.");
        }
    }

    private static void createWindow(TrafficController controller) {
        JFrame frame = new JFrame("VegamTraffic Dispatcher");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

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
        JLabel severity = new JLabel();
        JLabel assignedVehicle = new JLabel();
        JTextArea description = new JTextArea(3, 20);
        JButton submit = new JButton("Create routing request");

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        addField(form, "Vehicle type", vehicleTypes);
        addField(form, "Situation", situations);
        addField(form, "Priority", severity);
        addField(form, "Origin junction", origins);
        addField(form, "Destination junction", destinations);
        addField(form, "Description", new JScrollPane(description));
        form.add(new JLabel("Assigned vehicle"));
        form.add(assignedVehicle);
        form.add(new JLabel());
        form.add(submit);

        vehicleTypes.addActionListener(event -> {
            String type = (String) vehicleTypes.getSelectedItem();
            updateForm(controller, type, situations, severity, assignedVehicle, origins);
        });

        situations.addActionListener(event -> {
            String type = (String) vehicleTypes.getSelectedItem();
            String situation = (String) situations.getSelectedItem();
            if (situation != null) {
                severity.setText(DispatcherRequestFactory.getSeverity(type, situation));
            }
        });

        submit.addActionListener(event -> {
            try {
                addRequest(controller, (String) vehicleTypes.getSelectedItem(),
                        (String) situations.getSelectedItem(),
                        (String) origins.getSelectedItem(),
                        (String) destinations.getSelectedItem(),
                        description.getText());
                JOptionPane.showMessageDialog(frame, "Routing request added.");
                description.setText("");
            } catch (IllegalArgumentException exception) {
                JOptionPane.showMessageDialog(frame, exception.getMessage(),
                        "Request not added", JOptionPane.WARNING_MESSAGE);
            }
        });

        frame.setContentPane(form);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        updateForm(controller, (String) vehicleTypes.getSelectedItem(),
                situations, severity, assignedVehicle, origins);
    }

    private static void addField(JPanel panel, String label, java.awt.Component field) {
        panel.add(new JLabel(label));
        panel.add(field);
    }

    private static void updateVehicle(TrafficController controller, String vehicleType,
                                      JLabel assignedVehicle, JComboBox<String> origins) {
        EmergencyVehicle vehicle = DispatcherRequestFactory.findVehicle(
                controller, vehicleType);
        if (vehicle == null) {
            assignedVehicle.setText("None available");
        } else {
            assignedVehicle.setText(vehicle.getVehicleId()
                    + " at " + vehicle.getCurrentJunction());
            origins.setSelectedItem(vehicle.getCurrentJunction());
        }
    }

    private static void updateForm(TrafficController controller, String vehicleType,
                                  JComboBox<String> situations, JLabel severity,
                                  JLabel assignedVehicle, JComboBox<String> origins) {
        situations.removeAllItems();
        for (String situation : DispatcherRequestFactory.getSituations(vehicleType)) {
            situations.addItem(situation);
        }
        severity.setText(DispatcherRequestFactory.getSeverity(vehicleType,
                (String) situations.getSelectedItem()));
        updateVehicle(controller, vehicleType, assignedVehicle, origins);
    }
}
