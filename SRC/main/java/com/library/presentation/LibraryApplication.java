package com.library.presentation;

import com.library.data.DataSourceFactory;
import com.library.data.JdbcBookRepository;
import com.library.data.JdbcUserLookup;
import com.library.domain.User;
import com.library.security.Argon2PasswordHasher;
import com.library.security.AuthenticationService;
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
    private Stage stage;
    private CatalogService catalog;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        DataSourceFactory.DatabaseConfig config = new DataSourceFactory.DatabaseConfig(
                requiredEnvironment("LIBRARY_DB_URL"),
                requiredEnvironment("LIBRARY_DB_USER"),
                requiredEnvironment("LIBRARY_DB_PASSWORD"),
                Integer.parseInt(System.getenv().getOrDefault("LIBRARY_DB_POOL_SIZE", "10")));
        dataSource = DataSourceFactory.create(config);
        catalog = new CatalogService(new JdbcBookRepository(dataSource));
        AuthenticationService authentication = new AuthenticationService(
                new JdbcUserLookup(dataSource, 5),
                new Argon2PasswordHasher());

        FXMLLoader loader = new FXMLLoader(LibraryApplication.class.getResource("/view/login.fxml"));
        loader.setController(new LoginController(authentication, this::showCatalog));
        show(loader.load(), 560, 560);
        stage.setTitle("University Library — Sign in");
        stage.show();
    }

    private void showCatalog(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    LibraryApplication.class.getResource("/view/catalog.fxml"));
            loader.setController(new CatalogController(catalog));
            show(loader.load(), 900, 600);
            stage.setTitle("University Library — " + user.name() + " (" + user.role() + ")");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load catalog", failure);
        }
    }

    private void show(Parent root, int width, int height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(
                LibraryApplication.class.getResource("/view/library.css").toExternalForm());
        stage.setScene(scene);
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
