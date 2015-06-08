package org.dashbuilder.client.workbench.panels.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.inject.Inject;

import com.github.gwtbootstrap.client.ui.DropdownTab;
import com.github.gwtbootstrap.client.ui.Tab;
import com.github.gwtbootstrap.client.ui.TabLink;
import com.github.gwtbootstrap.client.ui.TabPane;
import com.github.gwtbootstrap.client.ui.TabPanel;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.BeforeSelectionEvent;
import com.google.gwt.event.logical.shared.BeforeSelectionHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.ComplexPanel;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import org.dashbuilder.client.perspective.editor.PerspectiveEditor;
import org.uberfire.client.resources.WorkbenchResources;
import org.uberfire.client.util.Layouts;
import org.uberfire.client.views.bs2.maximize.MaximizeToggleButton;
import org.uberfire.client.workbench.PanelManager;
import org.uberfire.client.workbench.panels.MaximizeToggleButtonPresenter;
import org.uberfire.client.workbench.panels.MultiPartWidget;
import org.uberfire.client.workbench.panels.WorkbenchPanelPresenter;
import org.uberfire.client.workbench.part.WorkbenchPartPresenter;
import org.uberfire.client.workbench.widgets.dnd.WorkbenchDragAndDropManager;
import org.uberfire.client.workbench.widgets.listbar.ResizeFocusPanel;
import org.uberfire.mvp.Command;
import org.uberfire.workbench.model.PartDefinition;

import static org.uberfire.commons.validation.PortablePreconditions.checkNotNull;

@Dependent
public class TabPanelWidget extends Composite
        implements MultiPartWidget, PanelToolbarWidget.Presenter, ClickHandler {

    interface TabPanelWidgetBinder extends UiBinder<ResizeFocusPanel, TabPanelWidget> {}
    static TabPanelWidgetBinder uiBinder = GWT.create(TabPanelWidgetBinder.class);

    protected static final int MARGIN = 20;

    @Inject
    protected PanelManager panelManager;

    @Inject
    protected PerspectiveEditor perspectiveEditor;

    @Inject
    protected WorkbenchDragAndDropManager dndManager;

    @Inject
    protected PanelToolbarWidget panelToolbarWidget;

    @UiField
    protected Panel panelToolbar;

    @UiField
    protected ResizeTabPanel tabPanel;

    protected WorkbenchPanelPresenter presenter;
    protected DropdownTab dropdownTab;
    protected List<WorkbenchPartPresenter> parts = new ArrayList<WorkbenchPartPresenter>();
    protected Map<WorkbenchPartPresenter.View, TabLink> tabIndex = new HashMap<WorkbenchPartPresenter.View, TabLink>();
    protected Map<TabLink, WorkbenchPartPresenter.View> tabInvertedIndex = new HashMap<TabLink, WorkbenchPartPresenter.View>();
    protected Map<PartDefinition, TabLink> partTabIndex = new HashMap<PartDefinition, TabLink>();
    protected boolean hasFocus = false;
    protected List<Command> focusGainedHandlers = new ArrayList<Command>();

    /**
     * Flag protecting {@link #updateDisplayedTabs()} from recursively invoking itself through events that it causes.
     */
    protected boolean updating;

    @PostConstruct
    void postConstruct() {
        initWidget(uiBinder.createAndBindUi(this));
        setup();
        Layouts.setToFillParent(this);
    }

    protected void setup() {
        dropdownTab = new DropdownTab( "More..." );

        tabPanel.addShownHandler( new TabPanel.ShownEvent.Handler() {
            @Override
            public void onShow( final TabPanel.ShownEvent e ) {
                onResize();
                if ( e.getRelatedTarget() != null ) {
                    BeforeSelectionEvent.fire(TabPanelWidget.this, tabInvertedIndex.get(e.getRelatedTarget()).getPresenter().getDefinition());
                }
            }
        } );

        tabPanel.addShowHandler(new TabPanel.ShowEvent.Handler() {
            @Override
            public void onShow(final TabPanel.ShowEvent e) {
                if (e.getTarget() == null) {
                    return;
                }
                SelectionEvent.fire(TabPanelWidget.this, tabInvertedIndex.get(e.getTarget()).getPresenter().getDefinition());
            }
        });

        tabPanel.addDomHandler( TabPanelWidget.this, ClickEvent.getType() );

        // Init the panel toolbar
        panelToolbarWidget.setPresenter(this);
        panelToolbarWidget.setEditEnabled(perspectiveEditor.isEditOn());
        panelToolbar.add(panelToolbarWidget);
    }

    @Override
    public void setPresenter( final WorkbenchPanelPresenter presenter ) {
        this.presenter = presenter;
    }

    @Override
    public void clear() {
        parts.clear();
        tabPanel.clear();
        dropdownTab.clear();
        partTabIndex.clear();
        tabIndex.clear();
        tabInvertedIndex.clear();
        panelToolbar.clear();
    }

    /**
     * Updates the display ({@link #tabPanel}) to reflect the current desired state of this tab panel.
     */
    protected void updateDisplayedTabs() {
        if ( updating ) {
            return;
        }
        try {
            updating = true;
            tabPanel.clear();
            dropdownTab.clear();

            if ( parts.size() == 0 ) {
                return;
            }

            int availableSpace = tabPanel.getOffsetWidth();
            TabLink selectedTab = null;

            // add and measure all tabs
            for ( int i = 0; i < parts.size(); i++ ) {
                WorkbenchPartPresenter part = parts.get( i );
                TabLink tabWidget = partTabIndex.get( part.getDefinition() );
                if ( tabWidget.isActive() ) {
                    selectedTab = tabWidget;
                }
                tabWidget.setActive( false );
                tabPanel.add( tabWidget );
                availableSpace -= tabWidget.getOffsetWidth();
            }

            // if we didn't find any selected tab, let's select the first one
            if ( selectedTab == null ) {
                TabLink firstTab = (TabLink) getTabs().getWidget( 0 );
                selectedTab = firstTab;
            }

            // now work from right to left to find out how many tabs we have to collapse into the dropdown
            if ( availableSpace < 0 ) {
                LinkedList<TabLink> newDropdownContents = new LinkedList<TabLink>();
                dropdownTab.setText( "More..." );
                tabPanel.add( dropdownTab );
                while ( availableSpace - dropdownTab.getOffsetWidth() < 0 && getTabs().getWidgetCount() > 1 ) {
                    // get the last tab that isn't the dropdown tab
                    TabLink tabWidget = (TabLink) getTabs().getWidget( getTabs().getWidgetCount() - 2 );
                    availableSpace += tabWidget.getOffsetWidth();
                    tabPanel.remove( tabWidget );
                    newDropdownContents.addFirst( tabWidget );
                    if ( tabWidget == selectedTab ) {
                        dropdownTab.setText( tabInvertedIndex.get( selectedTab ).getPresenter().getTitle() );
                    }
                }

                for ( TabLink l : newDropdownContents ) {
                    dropdownTab.add( l );
                    getTabContent().add( l.getTabPane() );
                }
            }

            selectedTab.show();
            updatePanelToolbar();

        } finally {
            updating = false;
        }
    }

    @Override
    public boolean selectPart( final PartDefinition id ) {
        final TabLink tab = partTabIndex.get( id );
        if ( tab != null ) {
            tab.show();
        }
        return false;
    }

    @Override
    public boolean remove( final PartDefinition id ) {
        final TabLink tab = partTabIndex.get( id );
        if ( tab == null ) {
            return false;
        }
        int removedTabIndex = getTabs().getWidgetIndex( tab );
        final boolean wasActive = tab.isActive();

        WorkbenchPartPresenter.View partView = tabInvertedIndex.remove( tab );
        parts.remove( partView.getPresenter() );
        tabIndex.remove( partView );
        partTabIndex.remove( id );

        updateDisplayedTabs();
        updatePanelToolbar();

        if ( removedTabIndex >= 0 && wasActive && getTabs().getWidgetCount() > 0 ) {
            tabPanel.selectTab( removedTabIndex <= 0 ? 0 : removedTabIndex - 1 );
        }

        return true;
    }

    @Override
    public void changeTitle( final PartDefinition id,
            final String title,
            final IsWidget titleDecoration ) {
        final TabLink tabLink = partTabIndex.get( id );
        if ( tabLink != null ) {
            tabLink.setText( title );
        }
    }

    @Override
    public HandlerRegistration addBeforeSelectionHandler( final BeforeSelectionHandler<PartDefinition> handler ) {
        return addHandler( handler, BeforeSelectionEvent.getType() );
    }

    @Override
    public HandlerRegistration addSelectionHandler( final SelectionHandler<PartDefinition> handler ) {
        return addHandler( handler, SelectionEvent.getType() );
    }

    @Override
    public void addPart( final WorkbenchPartPresenter.View partView ) {
        if ( !tabIndex.containsKey( partView ) ) {
            final Tab newTab = createTab( partView, false, 0, 0 );
            parts.add(partView.getPresenter());
            tabIndex.put(partView, newTab.asTabLink());
            updateDisplayedTabs();
            updatePanelToolbar();
        }
    }

    /**
     * The GwtBootstrap TabPanel doesn't support the RequiresResize/ProvidesResize contract, and UberTabPanel fills in
     * the gap. This helper method allows us to call ok
     * () on the widgets that need it.
     *
     * @param widget the widget that has just been resized
     */
    private void resizeIfNeeded( final Widget widget ) {
        if ( isAttached() && widget instanceof RequiresResize ) {
            ( (RequiresResize) widget ).onResize();
        }
    }

    /**
     * Creates a tab widget for the given part view, adding it to the tab/partDef/tabLink maps.
     */
    Tab createTab( final WorkbenchPartPresenter.View view,
            final boolean isActive,
            final int width,
            final int height ) {

        final Tab tab = createTab( view, isActive );

        tab.addClickHandler(createTabClickHandler( view, tab ) );

        tab.add( view.asWidget() );

        resizeIfNeeded( view.asWidget() );

        tabIndex.put( view, tab.asTabLink() );
        tabInvertedIndex.put( tab.asTabLink(), view );
        partTabIndex.put( view.getPresenter().getDefinition(), tab.asTabLink() );

        dndManager.makeDraggable(view, tab.asTabLink().getWidget( 0 ) );
        return tab;
    }

    /**
     * Subroutine of {@link #createTab(WorkbenchPartPresenter.View, boolean, int, int)}. Exposed for testing. Never call this except from
     * within the other createTab method.
     */
    Tab createTab( final WorkbenchPartPresenter.View view,
            final boolean isActive ) {
        Tab tab = new Tab();
        tab.setHeading( view.getPresenter().getTitle() );
        tab.setActive( isActive );
        return tab;
    }

    private ClickHandler createTabClickHandler( final WorkbenchPartPresenter.View view,
            final Tab tab ) {
        return new ClickHandler() {
            @Override
            public void onClick( final ClickEvent event ) {
                TabPanelWidget.this.onClick( event );
            }
        };
    }

    @Override
    public void onResize() {
        final int width = getOffsetWidth();
        final int height = getOffsetHeight();

        updateDisplayedTabs();

        TabLink selectedTab = getSelectedTab();
        if ( selectedTab != null ) {
            final TabPane tabPane = selectedTab.getTabPane();
            Widget tabPaneContent = tabPane.getWidget( 0 );
            tabPaneContent.setPixelSize( width, height - getTabHeight() );
            resizeIfNeeded(tabPaneContent);
        }
    }

    private int getTabHeight() {
        return tabPanel.getWidget( 0 ).getOffsetHeight() + MARGIN;
    }

    /**
     * Returns the panel (from inside {@link #tabPanel}) that contains the panel content for each tab. Each child of the
     * returned panel is the GUI that will be shown then its corresponding tab is selected.
     */
    private ComplexPanel getTabContent() {
        return (ComplexPanel) tabPanel.getWidget( 1 );
    }

    /**
     * Returns the panel (from inside {@link #tabPanel}) that contains the tab widgets. Each child of the returned panel
     * is a tab in the GUI.
     */
    private ComplexPanel getTabs() {
        return (ComplexPanel) tabPanel.getWidget( 0 );
    }

    @Override
    public void setDndManager( final WorkbenchDragAndDropManager dndManager ) {
        this.dndManager = dndManager;
    }

    @Override
    public void setFocus( final boolean hasFocus ) {
        this.hasFocus = hasFocus;
        if ( hasFocus ) {
            getTabs().setStyleName( WorkbenchResources.INSTANCE.CSS().activeNavTabs(), true );
        } else {
            getTabs().removeStyleName( WorkbenchResources.INSTANCE.CSS().activeNavTabs() );
        }
    }

    @Override
    public void onClick( final ClickEvent event ) {
        if ( !hasFocus ) {
            fireFocusGained();
            WorkbenchPartPresenter.View view = getSelectedPart();
            if ( view != null ) {
                SelectionEvent.fire( TabPanelWidget.this, view.getPresenter().getDefinition() );
            }
        }
    }

    /**
     * Gets the selected tab, even if it's nested in the DropdownTab. Returns null if no tab is selected.
     */
    private TabLink getSelectedTab() {
        for ( TabLink tab : tabInvertedIndex.keySet() ) {
            if ( tab.isActive() ) {
                return tab;
            }
        }
        return null;
    }

    public WorkbenchPartPresenter.View getSelectedPart() {
        return tabInvertedIndex.get( getSelectedTab() );
    }

    private void fireFocusGained() {
        for ( int i = focusGainedHandlers.size() - 1; i >= 0; i-- ) {
            focusGainedHandlers.get( i ).execute();
        }
    }

    @Override
    public void addOnFocusHandler( final Command doWhenFocused ) {
        focusGainedHandlers.add( checkNotNull( "doWhenFocused", doWhenFocused ) );
    }

    @Override
    public int getPartsSize() {
        return partTabIndex.size();
    }

    /**
     * Returns the toggle button, which is initially hidden, that can be used to trigger maximizing and unmaximizing
     * of the panel containing this list bar. Make the button visible by calling {@link Widget#setVisible(boolean)}
     * and set its maximize and unmaximize actions with {@link MaximizeToggleButton#setMaximizeCommand(Command)} and
     * {@link MaximizeToggleButton#setUnmaximizeCommand(Command)}.
     */
    public MaximizeToggleButtonPresenter getMaximizeButton() {
        return panelToolbarWidget.getMaximizeButton();
    }

    // Panel Toolbar stuff

    protected WorkbenchPartPresenter.View getCurrentPart() {
        TabLink selectedTab = null;
        for ( int i = 0; i < parts.size(); i++ ) {
            WorkbenchPartPresenter part = parts.get( i );
            TabLink tabWidget = partTabIndex.get( part.getDefinition() );
            if ( tabWidget.isActive() ) {
                selectedTab = tabWidget;
            }
        }
        if ( selectedTab == null ) {
            TabLink firstTab = (TabLink) getTabs().getWidget( 0 );
            selectedTab = firstTab;
        }
        return tabInvertedIndex.get(selectedTab);
    }

    protected void updatePanelToolbar() {
        panelToolbarWidget.setCurrentPart(getCurrentPart());
        panelToolbarWidget.setAvailableParts(null);
        panelToolbarWidget.updateView();
    }

    @Override
    public void selectPart(WorkbenchPartPresenter.View partView) {
        this.selectPart(partView.getPresenter().getDefinition());
    }

    @Override
    public void closePart(WorkbenchPartPresenter.View partView) {
        perspectiveEditor.closePart(partView.getPresenter().getDefinition());
        perspectiveEditor.saveCurrentPerspective();
    }

    @Override
    public void changePanelType(String panelType) {
        perspectiveEditor.changePanelType(presenter, panelType);
        perspectiveEditor.saveCurrentPerspective();
    }

    @Override
    public String getPanelType() {
        return presenter.getDefinition().getPanelType();
    }

    @Override
    public Map<String,String> getAvailablePanelTypes() {
        Map<String,String> result = new HashMap<String, String>();
        result.put(MultiListWorkbenchPanelPresenterExt.class.getName(), "List");
        if (tabIndex.size() == 1) {
            result.put(StaticWorkbenchPanelPresenterExt.class.getName(), "Static");
        }
        return result;
    }
}
