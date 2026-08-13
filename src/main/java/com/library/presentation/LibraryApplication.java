package com.library.presentation;

import com.library.data.DataSourceFactory;
import com.library.data.DatabaseDiagnostic;
import com.library.data.JdbcAuditRepository;
import com.library.data.JdbcBookRepository;
import com.library.data.JdbcCirculationReportRepository;
import com.library.data.JdbcFineEventRepository;
import com.library.data.JdbcFineRepository;
import com.library.data.JdbcHoldRepository;
import com.library.data.JdbcLoanPolicyRepository;
import com.library.data.JdbcLoanTransactionManager;
import com.library.data.JdbcNotificationRepository;
import com.library.data.JdbcRecommendationRepository;
import com.library.data.JdbcUserAdminRepository;
import com.library.data.JdbcUserLookup;
import com.library.domain.LoanPolicy;
import com.library.domain.User;
import com.library.security.Argon2PasswordHasher;
import com.library.security.AuthenticationService;
import com.library.security.AuthorizationService;
import com.library.security.SessionGuard;
import com.library.service.AuditService;
import com.library.service.CatalogService;
import com.library.service.CirculationReportService;
import com.library.service.CirculationService;
import com.library.service.ExportService;
import com.library.service.FineService;
import com.library.service.HoldService;
import com.library.service.LoanPolicyService;
import com.library.service.NotificationService;
import com.library.service.RecommendationService;
import com.library.service.UserAdminService;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

public final class LibraryApplication extends Application {
    private HikariDataSource dataSource;
    private Stage stage;
    private CatalogService catalog;
    private CirculationService circulation;
    private CirculationReportService circulationReports;
    private ExportService exports;
    private FineService fines;
    private RecommendationService recommendations;
    private UserAdminService userAdmin;
    private LoanPolicyService loanPolicies;
    private AuthorizationService authorization;
    private AuthenticationService authentication;
    private AuthenticationService.UserLookup userLookup;
    private AuditService audit;

    @Override
    public void start(Stage stage) throws IOException {
        this.stage = stage;
        try {
            DataSourceFactory.DatabaseConfig config = new DataSourceFactory.DatabaseConfig(
                    requiredEnvironment("LIBRARY_DB_URL"),
                    requiredEnvironment("LIBRARY_DB_USER"),
                    requiredEnvironment("LIBRARY_DB_PASSWORD"),
                    Integer.parseInt(System.getenv().getOrDefault("LIBRARY_DB_POOL_SIZE", "10")));
            dataSource = DataSourceFactory.create(config);
            DatabaseDiagnostic.verify(dataSource);
        } catch (SQLException | RuntimeException failure) {
            closeDataSource();
            showDatabaseUnavailable();
            return;
        }
        JdbcUserAdminRepository userAccounts = new JdbcUserAdminRepository(dataSource);
        authorization = new AuthorizationService(new SessionGuard(userAccounts));
        audit = new AuditService(
                new JdbcAuditRepository(dataSource, true), authorization);
        JdbcLoanTransactionManager loanTransactions = new JdbcLoanTransactionManager(dataSource);
        JdbcBookRepository bookRepository = new JdbcBookRepository(dataSource, true);
        catalog = new CatalogService(
                bookRepository,
                loanTransactions,
                authorization,
                audit);
        JdbcFineRepository fineRepository = new JdbcFineRepository(dataSource);
        NotificationService notifications = new NotificationService(
                new JdbcNotificationRepository(dataSource),
                authorization,
                Clock.systemUTC());
        HoldService holdService = new HoldService(
                new JdbcHoldRepository(dataSource),
                authorization,
                audit,
                notifications,
                Clock.systemUTC());
        JdbcLoanPolicyRepository policyRepository = new JdbcLoanPolicyRepository(dataSource, true);
        loanPolicies = new LoanPolicyService(policyRepository, authorization, audit);
        LoanPolicy policy;
        try {
            policy = loanPolicies.current();
        } catch (SQLException failure) {
            throw new IllegalStateException("Unable to load loan policy", failure);
        }
        circulation = new CirculationService(
                loanTransactions,
                fineRepository,
                holdService,
                authorization,
                audit,
                Clock.systemDefaultZone(),
                loanPolicies,
                policy.maxRenewals());
        circulationReports = new CirculationReportService(
                new JdbcCirculationReportRepository(dataSource), authorization);
        exports = new ExportService(bookRepository, loanTransactions, fineRepository, authorization);
        fines = new FineService(
                fineRepository,
                new JdbcFineEventRepository(dataSource),
                authorization,
                audit,
                Clock.systemUTC());
        recommendations = new RecommendationService(
                new JdbcRecommendationRepository(dataSource), authorization);
        Argon2PasswordHasher passwordHasher = new Argon2PasswordHasher();
        userLookup = new JdbcUserLookup(dataSource, policy.borrowLimit());
        authentication = new AuthenticationService(
                userLookup,
                userAccounts,
                passwordHasher,
                Clock.systemUTC());
        userAdmin = new UserAdminService(
                userAccounts,
                passwordHasher,
                authorization,
                audit);

        showLogin();
        stage.show();
    }

    private void showLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    LibraryApplication.class.getResource("/view/login.fxml"));
            loader.setController(new LoginController(authentication, this::showCatalog));
            show(loader.load(), 560, 560);
            stage.setTitle("University Library — Sign in");
        } catch (IOException failure) {
            throw new IllegalStateException("Unable to load login", failure);
        }
    }

    private void showCatalog(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    LibraryApplication.class.getResource("/view/catalog.fxml"));
            loader.setController(new CatalogController(
                    catalog,
                    circulation,
                    circulationReports,
                    fines,
                    recommendations,
                    audit,
                    userAdmin,
                    loanPolicies,
                    exports,
                    authentication,
                    userLookup,
                    user,
                    authorization,
                    this::showLogin));
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
        closeDataSource();
    }

    private void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private void showDatabaseUnavailable() {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setTitle("Database unavailable");
        alert.setHeaderText("Cannot connect to the library database");
        alert.setContentText(
                "The application could not start. Check that the database is running and "
                        + "the connection settings are correct, then try again.");
        alert.showAndWait();
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
