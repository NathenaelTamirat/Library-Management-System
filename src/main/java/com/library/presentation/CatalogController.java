package com.library.presentation;

import com.library.domain.Book;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.ReturnReceipt;
import com.library.domain.User;
import com.library.security.AuthenticationService;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import com.library.service.AuditService;
import com.library.service.CatalogService;
import com.library.service.CirculationService;
import com.library.service.FineService;
import com.library.service.RecommendationService;
import com.library.service.UserAdminService;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

public final class CatalogController {
    private final CatalogService catalog;
    private final CirculationService circulation;
    private final FineService fines;
    private final RecommendationService recommendations;
    private final AuditService audits;
    private final UserAdminService userAdmin;
    private final AuthenticationService authentication;
    private final User currentUser;
    private final AuthorizationService authorization;
    private final Runnable onSignOut;

    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private Button recommendationsButton;
    @FXML
    private Button borrowButton;
    @FXML
    private Button returnButton;
    @FXML
    private Button finesButton;
    @FXML
    private Button myLoansButton;
    @FXML
    private Button overdueButton;
    @FXML
    private Button auditButton;
    @FXML
    private Button usersButton;
    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Button changePasswordButton;
    @FXML
    private Button signOutButton;
    @FXML
    private TableView<Book> resultsTable;
    @FXML
    private TableColumn<Book, String> isbnColumn;
    @FXML
    private TableColumn<Book, String> titleColumn;
    @FXML
    private TableColumn<Book, String> authorColumn;
    @FXML
    private TableColumn<Book, Number> availableColumn;
    @FXML
    private Label statusLabel;

    public CatalogController(
            CatalogService catalog,
            CirculationService circulation,
            FineService fines,
            RecommendationService recommendations,
            AuditService audits,
            UserAdminService userAdmin,
            AuthenticationService authentication,
            User currentUser,
            AuthorizationService authorization,
            Runnable onSignOut) {
        this.catalog = catalog;
        this.circulation = circulation;
        this.fines = fines;
        this.recommendations = recommendations;
        this.audits = audits;
        this.userAdmin = userAdmin;
        this.authentication = authentication;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.onSignOut = onSignOut;
    }

    @FXML
    private void initialize() {
        isbnColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().isbn()));
        titleColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().title()));
        authorColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().author()));
        availableColumn.setCellValueFactory(data ->
                new SimpleIntegerProperty(data.getValue().availableCopies()));
        isbnColumn.setSortable(true);
        titleColumn.setSortable(true);
        authorColumn.setSortable(true);
        availableColumn.setSortable(true);
        resultsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        searchField.setOnAction(ignored -> search());
        boolean member = currentUser instanceof Member
                && authorization.isAllowed(currentUser.role(), Permission.BORROW_BOOK);
        recommendationsButton.setVisible(member);
        recommendationsButton.setManaged(member);
        borrowButton.setVisible(member);
        borrowButton.setManaged(member);
        boolean canReturn = authorization.isAllowed(currentUser.role(), Permission.MANAGE_LOANS)
                || (currentUser instanceof Member
                        && authorization.isAllowed(currentUser.role(), Permission.BORROW_BOOK));
        returnButton.setVisible(canReturn);
        returnButton.setManaged(canReturn);
        boolean showFines = currentUser instanceof Member
                || authorization.isAllowed(currentUser.role(), Permission.MANAGE_LOANS);
        finesButton.setVisible(showFines);
        finesButton.setManaged(showFines);
        boolean showMyLoans = currentUser instanceof Member
                || authorization.isAllowed(currentUser.role(), Permission.MANAGE_LOANS);
        myLoansButton.setVisible(showMyLoans);
        myLoansButton.setManaged(showMyLoans);
        boolean manageLoans = authorization.isAllowed(currentUser.role(), Permission.MANAGE_LOANS);
        overdueButton.setVisible(manageLoans);
        overdueButton.setManaged(manageLoans);
        boolean viewAudit = authorization.isAllowed(currentUser.role(), Permission.VIEW_AUDIT_LOG);
        auditButton.setVisible(viewAudit);
        auditButton.setManaged(viewAudit);
        boolean manageUsers = authorization.isAllowed(currentUser.role(), Permission.MANAGE_USERS);
        usersButton.setVisible(manageUsers);
        usersButton.setManaged(manageUsers);
        boolean catalogManager = authorization.isAllowed(
                currentUser.role(), Permission.MANAGE_CATALOG);
        addButton.setVisible(catalogManager);
        addButton.setManaged(catalogManager);
        editButton.setVisible(catalogManager);
        editButton.setManaged(catalogManager);
        deleteButton.setVisible(catalogManager);
        deleteButton.setManaged(catalogManager);
    }

    @FXML
    private void search() {
        String query = searchField.getText();
        Task<List<Book>> searchTask = createSearchTask(query);
        searchButton.disableProperty().bind(searchTask.runningProperty());
        statusLabel.textProperty().bind(searchTask.messageProperty());
        searchTask.setOnSucceeded(ignored -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText(searchTask.getValue().size() + " book(s) found");
            resultsTable.setItems(FXCollections.observableArrayList(searchTask.getValue()));
        });
        searchTask.setOnFailed(ignored -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Search failed: " + searchTask.getException().getMessage());
        });

        Thread worker = new Thread(searchTask, "catalog-search");
        worker.setDaemon(true);
        worker.start();
    }

    private Book selectedBook() {
        return resultsTable.getSelectionModel().getSelectedItem();
    }

    @FXML
    private void showRecommendations() {
        if (!(currentUser instanceof Member member)) {
            statusLabel.setText("Recommendations are available to members");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/recommendations.fxml"));
            loader.setController(new RecommendationController(
                    recommendations, circulation, member));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Recommended for " + member.name());
            Scene scene = new Scene(root, 680, 520);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
            search();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open recommendations: " + failure.getMessage());
        }
    }

    @FXML
    private void borrowSelected() {
        Book selected = selectedBook();
        if (selected == null || !(currentUser instanceof Member member)) {
            statusLabel.setText("Select a book to borrow");
            return;
        }
        Task<Loan> task = new Task<>() {
            @Override
            protected Loan call() throws Exception {
                return circulation.checkout(member, selected.isbn());
            }
        };
        borrowButton.disableProperty().bind(task.runningProperty());
        statusLabel.setText("Checking out…");
        task.setOnSucceeded(ignored -> {
            showCheckoutSuccess(task.getValue());
            search();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Operation failed: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "catalog-checkout");
        worker.setDaemon(true);
        worker.start();
    }

    private void showCheckoutSuccess(Loan loan) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Checkout complete");
        alert.setHeaderText("Book borrowed successfully");
        alert.setContentText("Loan ID: " + loan.id()
                + "\nISBN: " + loan.isbn()
                + "\nDue date: " + loan.dueDate());
        alert.showAndWait();
        statusLabel.setText("Checked out until " + loan.dueDate() + " (loan " + loan.id() + ")");
    }

    @FXML
    private void addBook() {
        openEditor(Optional.empty());
    }

    @FXML
    private void editSelected() {
        Book selected = selectedBook();
        if (selected == null) {
            statusLabel.setText("Select a book to edit");
            return;
        }
        openEditor(Optional.of(selected));
    }

    @FXML
    private void returnSelected() {
        Book selected = selectedBook();
        if (selected == null) {
            statusLabel.setText("Select a book to return");
            return;
        }
        Task<ReturnReceipt> task = new Task<>() {
            @Override
            protected ReturnReceipt call() throws Exception {
                return circulation.returnSelectedBook(currentUser, selected.isbn());
            }
        };
        returnButton.disableProperty().bind(task.runningProperty());
        statusLabel.setText("Returning…");
        task.setOnSucceeded(ignored -> {
            showReturnReceipt(task.getValue());
            search();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Operation failed: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "catalog-return");
        worker.setDaemon(true);
        worker.start();
    }

    private void showReturnReceipt(ReturnReceipt receipt) {
        String fineText = receipt.fine()
                .map(fine -> fine.amount().toPlainString())
                .orElse("0.00");
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Return receipt");
        alert.setHeaderText("Return completed");
        alert.setContentText("Loan ID: " + receipt.loan().id()
                + "\nISBN: " + receipt.loan().isbn()
                + "\nFine: " + fineText);
        alert.showAndWait();
        statusLabel.setText("Returned loan " + receipt.loan().id() + " (fine " + fineText + ")");
    }

    @FXML
    private void signOut() {
        onSignOut.run();
    }

    @FXML
    private void showChangePassword() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/change-password.fxml"));
            loader.setController(new ChangePasswordController(authentication, currentUser));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Change password");
            Scene scene = new Scene(root, 420, 360);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open change password: " + failure.getMessage());
        }
    }

    @FXML
    private void showFines() {
        UUID memberId = currentUser.id();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/fines.fxml"));
            loader.setController(new FinesController(fines, currentUser, memberId, authorization));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Unpaid fines");
            Scene scene = new Scene(root, 560, 420);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open fines desk: " + failure.getMessage());
        }
    }

    @FXML
    private void showMyLoans() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/my-loans.fxml"));
            loader.setController(new MyLoansController(circulation, currentUser, currentUser.id()));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("My loans");
            Scene scene = new Scene(root, 640, 460);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
            search();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open loans desk: " + failure.getMessage());
        }
    }

    @FXML
    private void showOverdueLoans() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/overdue-loans.fxml"));
            loader.setController(new OverdueLoansController(circulation, currentUser));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Overdue loans");
            Scene scene = new Scene(root, 680, 480);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open overdue report: " + failure.getMessage());
        }
    }

    @FXML
    private void showAuditLog() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/audit-log.fxml"));
            loader.setController(new AuditLogController(audits, currentUser));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("Audit log");
            Scene scene = new Scene(root, 760, 520);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open audit log: " + failure.getMessage());
        }
    }

    @FXML
    private void showUserAdmin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/user-admin.fxml"));
            loader.setController(new UserAdminController(userAdmin, currentUser));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle("User administration");
            Scene scene = new Scene(root, 820, 640);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open user administration: " + failure.getMessage());
        }
    }

    @FXML
    private void deleteSelected() {
        Book selected = selectedBook();
        if (selected == null) {
            statusLabel.setText("Select a book to delete");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm delete");
        confirm.setHeaderText("Delete this book from the catalog?");
        confirm.setContentText(selected.title() + " (" + selected.isbn() + ")");
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.orElseThrow() != ButtonType.OK) {
            statusLabel.setText("Delete cancelled");
            return;
        }
        runMutation(deleteButton, "Deleting…", () ->
                catalog.delete(currentUser, selected.isbn()));
    }

    private void openEditor(Optional<Book> existing) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/book-editor.fxml"));
            loader.setController(new BookEditorController(
                    catalog, currentUser, existing, ignored -> search()));
            Parent root = loader.load();
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setTitle(existing.isPresent() ? "Edit book" : "Add book");
            Scene scene = new Scene(root, 480, 360);
            scene.getStylesheets().add(
                    getClass().getResource("/view/library.css").toExternalForm());
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException failure) {
            statusLabel.setText("Unable to open editor: " + failure.getMessage());
        }
    }

    private void runMutation(Button source, String message, CheckedOperation operation) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                operation.run();
                return null;
            }
        };
        source.disableProperty().bind(task.runningProperty());
        statusLabel.setText(message);
        task.setOnSucceeded(ignored -> search());
        task.setOnFailed(ignored ->
                statusLabel.setText("Operation failed: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "catalog-mutation");
        worker.setDaemon(true);
        worker.start();
    }

    Task<List<Book>> createSearchTask(String query) {
        return new Task<>() {
            @Override
            protected List<Book> call() throws Exception {
                updateMessage("Searching…");
                return catalog.search(query);
            }
        };
    }

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }
}
