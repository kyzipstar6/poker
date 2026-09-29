cd C:\Users\georg\Documents\java
javac --module-path   "speciesevolutionsimulator/lib/javafx-sdk-23.0.2/lib"  --add-modules javafx.controls,javafx.fxml,org.json,javafx.media,org.fxmisc.flowless,org.fxmisc.richtext,org.fxmisc.undo poker/src/main/java/eptsimulator/*.java -d poker/bin

java --module-path speciesevolutionsimulator/lib/javafx-sdk-23.0.2/lib --add-modules javafx.controls,javafx.fxml,org.json,javafx.media,org.fxmisc.flowless,org.fxmisc.richtext,org.fxmisc.undo -cp ".;poker/bin" eptsimulator.EptSimulatorApp

cmd /k
