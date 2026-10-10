package me.noramibu.tweaks.category;

import java.util.Objects;

public class CustomCategory {
    public String name;
    public SortOrder sortOrder = SortOrder.WEIGHT;

    public CustomCategory(String name) {
        this.name = name;
    }

    public CustomCategory() {}

    public void cycleSortOrder() {
        if (sortOrder == SortOrder.A_TO_Z) {
            sortOrder = SortOrder.Z_TO_A;
        } else if (sortOrder == SortOrder.Z_TO_A) {
            sortOrder = SortOrder.WEIGHT;
        } else {
            sortOrder = SortOrder.A_TO_Z;
        }
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomCategory that = (CustomCategory) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
