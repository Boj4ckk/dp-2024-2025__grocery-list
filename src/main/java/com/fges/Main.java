    package com.fges;

    import com.fasterxml.jackson.core.type.TypeReference;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import org.apache.commons.cli.CommandLine;
    import org.apache.commons.cli.CommandLineParser;
    import org.apache.commons.cli.DefaultParser;
    import org.apache.commons.cli.Options;
    import org.apache.commons.cli.ParseException;

    import java.io.File;
    import java.io.FileWriter;
    import java.io.IOException;
    import java.nio.file.Files;
    import java.nio.file.Path;
    import java.nio.file.Paths;
    import java.util.ArrayList;
    import java.util.List;

    public class Main {

        public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        public static void main(String[] args) throws IOException {
            System.exit(exec(args));
        }

        public static int exec(String[] args) throws IOException {
            // Configuration de l'interface CLI
            Options cliOptions = new Options();
            CommandLineParser parser = new DefaultParser();

            cliOptions.addRequiredOption("s", "source", true, "Fichier contenant la liste de courses");
            cliOptions.addOption("f", "format", true, "Format du fichier (json ou csv)");

            CommandLine cmd;
            try {
                cmd = parser.parse(cliOptions, args);
            } catch (ParseException ex) {
                System.err.println("Échec de l'analyse des arguments : " + ex.getMessage());
                return 1;

            }
            String fileName = cmd.getOptionValue("s");
            String fileFormat = cmd.getOptionValue("f");

            if (!fileFormat.equals("json") && !fileFormat.equals("csv")) {
                System.err.println("Format invalide : " + fileFormat + ". Doit être 'json' ou 'csv'");
                return 1;
            }

            List<String> positionalArgs = cmd.getArgList();
            if (positionalArgs.isEmpty()) {
                System.err.println("Commande manquante");
                return 1;
            }

            String command = positionalArgs.get(0);

            // Chargement de l'état actuel de la liste de courses
            List<GroceryItem> groceryList = loadGroceryList(fileName, fileFormat);

            // Interprétation de la commande
            switch (command) {
                case "add" -> {
                    if (positionalArgs.size() < 3) {
                        System.err.println("Arguments manquants");
                        return 1;
                    }

                    String itemName = positionalArgs.get(1);
                    try {
                        int quantity = Integer.parseInt(positionalArgs.get(2));
                        groceryList.add(new GroceryItem(itemName, quantity));
                        saveGroceryList(fileName, fileFormat, groceryList);
                        return 0;
                    } catch (NumberFormatException e) {
                        System.err.println("La quantité doit être un nombre");
                        return 1;
                    }
                }
                case "list" -> {
                    for (GroceryItem item : groceryList) {
                        System.out.println(item.getName() + ": " + item.getQuantity());
                    }
                    return 0;
                }
                case "remove" -> {
                    if (positionalArgs.size() < 2) {
                        System.err.println("Arguments manquants");
                        return 1;
                    }

                    String itemName = positionalArgs.get(1);
                    groceryList.removeIf(item -> item.getName().equals(itemName));
                    saveGroceryList(fileName, fileFormat, groceryList);
                    return 0;
                }
                case "format" -> {
                    if (positionalArgs.size() < 2) {
                        System.err.println("Arguments manquants");
                        return 1;
                    }
                    String newFormat = positionalArgs.get(1);
                    if (!newFormat.equals("json") && !newFormat.equals("csv")) {
                        System.err.println("Format invalide : " + newFormat + ". Doit être 'json' ou 'csv'");
                        return 1;
                    }

                    // Convertir le fichier existant au nouveau format
                    String newFileName = fileName;
                    if (fileName.contains(".")) {
                        newFileName = fileName.substring(0, fileName.lastIndexOf('.')) +
                                (newFormat.equals("json") ? ".json" : ".csv");
                    } else {
                        newFileName = fileName + "." + newFormat;
                    }
                    saveGroceryList(newFileName, newFormat, groceryList);
                    System.out.println("Fichier converti avec succès au format " + newFormat);
                    return 0;
                }
                default -> {
                    System.err.println("Commande inconnue : " + command);
                    return 1;
                }
            }
        }

        private static List<GroceryItem> loadGroceryList(String fileName, String fileFormat) throws IOException {
            Path filePath = Paths.get(fileName);
            List<GroceryItem> groceryList = new ArrayList<>();

            if (Files.exists(filePath)) {
                if (fileFormat.equals("json")) {
                    String fileContent = Files.readString(filePath);
                    try {
                        // Essayer d'abord le nouveau format (GroceryItem)
                        var parsedList = OBJECT_MAPPER.readValue(fileContent, new TypeReference<List<GroceryItem>>() {});
                        groceryList = new ArrayList<>(parsedList);
                    } catch (Exception e) {
                        // Si ça échoue, essayer l'ancien format (String)
                        var oldFormatList = OBJECT_MAPPER.readValue(fileContent, new TypeReference<List<String>>() {});
                        for (String item : oldFormatList) {
                            String[] parts = item.split(": ");
                            if (parts.length >= 2) {
                                try {
                                    String name = parts[0];
                                    int quantity = Integer.parseInt(parts[1]);
                                    groceryList.add(new GroceryItem(name, quantity));
                                } catch (NumberFormatException ex) {
                                    System.err.println("Format d'élément non valide : " + item);
                                }
                            }
                        }
                    }
                } else if (fileFormat.equals("csv")) {
                    groceryList = loadFromCSV(filePath);
                }
            }

            return groceryList;
        }


        private static List<GroceryItem> loadFromCSV(Path filePath) throws IOException {
            List<GroceryItem> groceryList = new ArrayList<>();
            List<String> lines = Files.readAllLines(filePath);

            if (lines.isEmpty()) {
                return groceryList;
            }


            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                String[] parts = line.split(";");
                if (parts.length >= 2) {
                    try {
                        String name = parts[0];
                        int quantity = Integer.parseInt(parts[1]);
                        groceryList.add(new GroceryItem(name, quantity));
                    } catch (NumberFormatException e) {
                        System.err.println("Avertissement : quantité invalide dans le fichier CSV à la ligne " + (i + 1));
                    }
                }
            }

            return groceryList;
        }


        private static void saveGroceryList(String fileName, String fileFormat, List<GroceryItem> groceryList) throws IOException {
            if (fileFormat.equals("json")) {
                var outputFile = new File(fileName);
                OBJECT_MAPPER.writeValue(outputFile, groceryList);
            } else if (fileFormat.equals("csv")) {
                saveToCSV(fileName, groceryList);
            }
        }

        private static void saveToCSV(String fileName, List<GroceryItem> groceryList) throws IOException {
            try (FileWriter writer = new FileWriter(fileName)) {

                writer.write("nom;quantite\n");


                for (GroceryItem item : groceryList) {
                    writer.write(item.getName() + ";" + item.getQuantity() + "\n");
                }
            }
        }
    }