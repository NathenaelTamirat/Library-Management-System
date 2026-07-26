package com.library.presentation;

import com.library.data.DataSourceFactory;
import com.library.data.JdbcAuditRepository;
import com.library.data.JdbcBookRepository;
import com.library.data.JdbcFineRepository;
import com.library.data.JdbcLoanTransactionManager;
import com.library.data.JdbcRecommendationRepository;
import com.library.data.JdbcUserAdminRepository;
import com.library.data.JdbcUserLookup;
import com.library.domain.User;
import com.library.security.Argon2PasswordHasher;
import com.library.security.AuthenticationService;
import com.library.security.AuthorizationService;
import com.library.service.AuditService;
import com.library.service.CatalogService;
import com.library.service.CirculationService;
import com.library.service.FineService;
import com.library.service.RecommendationService;
import com.library.service.UserAdminService;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.time.Clock;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class LibraryApplication extends Application {
    private HikariDataSource dataSource;
    private Stage stage;
    private CatalogService catalog;
    private CirculationService circulation;
    private FineService fines;
    private RecommendationService recommendations;
    private UserAdminService userAdmin;
    private AuthorizationService authorization;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        DataSourceFactory.DatabaseConfig config = new DataSourceFactory.DatabaseConfig(
                requiredEnvironment("LIBRARY_DB_URL"),
                requiredEnvironment("LIBRARY_DB_USER"),
                requiredEnvironment("LIBRARY_DB_PASSWORD"),
                Integer.parseInt(System.getenv().getOrDefault("LIBRARY_DB_POOL_SIZE", "10")));
        dataSource = DataSourceFactory.create(config);
        authorization = new AuthorizationService();
        AuditService audit = new AuditService(
                new JdbcAuditRepository(dataSource, true), authorization);
        JdbcLoanTransactionManager loanTransactions = new JdbcLoanTransactionManager(dataSource);
        catalog = new CatalogService(
                new JdbcBookRepository(dataSource), loanTransactions, authorization, audit);
        JdbcFineRepository fineRepository = new JdbcFineRepository(dataSource);
        circulation = new CirculationService(
                loanTransactions,
                fineRepository,
                authorization,
                audit,
                Clock.systemDefaultZone(),
                14);
        fines = new FineService(fineRepository, authorization, audit);
        recommendations = new RecommendationService(
                new JdbcRecommendationRepository(dataSource), authorization);
        Argon2PasswordHasher passwordHasher = new Argon2PasswordHasher();
        JdbcUserAdminRepository userAccounts = new JdbcUserAdminRepository(dataSource);
        AuthenticationService authentication = new AuthenticationService(
                new JdbcUserLookup(dataSource, 5),
                userAccounts,
                passwordHasher,
                Clock.systemUTC());
        userAdmin = new UserAdminService(
                userAccounts,
                passwordHasher,
                authorization,
                audit);

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
            loader.setController(new CatalogController(
                    catalog, circulation, recommendations, user, authorization));
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
