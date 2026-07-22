package io.apicurio.registry.cli.interactive;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class InteractiveTableStateTest {

    private InteractiveTableState<String> newState(String... rows) {
        return new InteractiveTableState<>(List.of(rows), s -> s);
    }

    @Test
    public void testInitialStateShowsAllRows() {
        var state = newState("alpha", "beta", "gamma");
        assertThat(state.getVisibleRows()).containsExactly("alpha", "beta", "gamma");
        assertThat(state.getSelectedIndex()).isEqualTo(0);
        assertThat(state.getMode()).isEqualTo(InteractiveTableState.Mode.NORMAL);
    }

    @Test
    public void testMoveDownAndUpChangesSelection() {
        var state = newState("alpha", "beta", "gamma");
        state.moveDown();
        assertThat(state.getSelectedIndex()).isEqualTo(1);
        state.moveUp();
        assertThat(state.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    public void testMoveUpAtTopStaysAtZero() {
        var state = newState("alpha", "beta");
        state.moveUp();
        assertThat(state.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    public void testMoveDownAtBottomStaysAtLast() {
        var state = newState("alpha", "beta");
        state.moveDown();
        state.moveDown();
        state.moveDown();
        assertThat(state.getSelectedIndex()).isEqualTo(1);
    }

    @Test
    public void testMoveOnEmptyListIsNoOp() {
        var state = newState();
        state.moveDown();
        state.moveUp();
        assertThat(state.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    public void testGetSelectedRowReturnsNullWhenEmpty() {
        var state = newState();
        assertThat(state.getSelectedRow()).isNull();
    }

    @Test
    public void testGetSelectedRowReturnsCorrectRow() {
        var state = newState("alpha", "beta", "gamma");
        state.moveDown();
        assertThat(state.getSelectedRow()).isEqualTo("beta");
    }

    @Test
    public void testStartFilterInputSetsMode() {
        var state = newState("alpha", "beta");
        state.startFilterInput();
        assertThat(state.getMode()).isEqualTo(InteractiveTableState.Mode.FILTER_INPUT);
    }

    @Test
    public void testTypeFilterCharNarrowsRowsCaseInsensitive() {
        var state = newState("Apple", "Banana", "Apricot");
        state.startFilterInput();
        state.typeFilterChar('a');
        state.typeFilterChar('p');
        assertThat(state.getVisibleRows()).containsExactly("Apple", "Apricot");
        assertThat(state.getSelectedIndex()).isEqualTo(0);
    }

    @Test
    public void testBackspaceFilterCharWidensRows() {
        var state = newState("Apple", "Banana", "Apricot");
        state.startFilterInput();
        state.typeFilterChar('a');
        state.typeFilterChar('p');
        state.typeFilterChar('x');
        assertThat(state.getVisibleRows()).isEmpty();
        state.backspaceFilterChar();
        assertThat(state.getVisibleRows()).containsExactly("Apple", "Apricot");
    }

    @Test
    public void testCommitFilterReturnsToNormalModeKeepingFilteredRows() {
        var state = newState("Apple", "Banana", "Apricot");
        state.startFilterInput();
        state.typeFilterChar('a');
        state.commitFilter();
        assertThat(state.getMode()).isEqualTo(InteractiveTableState.Mode.NORMAL);
        assertThat(state.getVisibleRows()).containsExactly("Apple", "Banana", "Apricot");
    }

    @Test
    public void testClearFilterResetsEverything() {
        var state = newState("Apple", "Banana", "Apricot");
        state.startFilterInput();
        state.typeFilterChar('a');
        state.moveDown();
        state.clearFilter();
        assertThat(state.getVisibleRows()).containsExactly("Apple", "Banana", "Apricot");
        assertThat(state.getFilterText()).isEmpty();
        assertThat(state.getSelectedIndex()).isEqualTo(0);
        assertThat(state.getMode()).isEqualTo(InteractiveTableState.Mode.NORMAL);
    }

    @Test
    public void testSetRowsResetsSelectionAndFilter() {
        var state = newState("alpha", "beta", "gamma");
        state.moveDown();
        state.startFilterInput();
        state.typeFilterChar('a');
        state.setRows(List.of("delta", "epsilon"));
        assertThat(state.getVisibleRows()).containsExactly("delta", "epsilon");
        assertThat(state.getSelectedIndex()).isEqualTo(0);
        assertThat(state.getFilterText()).isEmpty();
        assertThat(state.getMode()).isEqualTo(InteractiveTableState.Mode.NORMAL);
    }

    @Test
    public void testFilterWithNoMatchesLeavesEmptyRows() {
        var state = newState("Apple", "Banana");
        state.startFilterInput();
        state.typeFilterChar('z');
        assertThat(state.getVisibleRows()).isEmpty();
        assertThat(state.getSelectedRow()).isNull();
    }
}