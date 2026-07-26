package com.library.presentation;

import com.library.data.DataSourceFactory;
import com.library.data.JdbcBookRepository;
import com.library.service.CatalogService;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class LibraryApplication extends Application {
    private HikariDataSource dataSource;

    @Override
    public void start(Stage stage) throws IOException {
        DataSourceFactory.DatabaseConfig config = new DataSourceFactory.DatabaseConfig(
                requiredEnvironment("LIBRARY_DB_URL"),
                requiredEnvironment("LIBRARY_DB_USER"),
                requiredEnvironment("LIBRARY_DB_PASSWORD"),
                Integer.parseInt(System.getenv().getOrDefault("LIBRARY_DB_POOL_SIZE", "10")));
        dataSource = DataSourceFactory.create(config);
        CatalogService catalog = new CatalogService(new JdbcBookRepository(dataSource));

        FXMLLoader loader = new FXMLLoader(
                LibraryApplication.class.getResource("/view/catalog.fxml"));
        loader.setController(new CatalogController(catalog));
        Parent root = loader.load();
        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(
                LibraryApplication.class.getResource("/view/library.css").toExternalForm());

        stage.setTitle("University Library");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing environment variable: " + name);
        }
        return value;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
