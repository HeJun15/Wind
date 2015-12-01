package com.github.davidmoten.rtree;

import com.github.davidmoten.rtree.geometry.Geometry;
import com.github.davidmoten.rtree.geometry.HasGeometry;
import com.github.davidmoten.util.ObjectsHelper;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

/**
 * An entry in the R-tree which has a spatial representation.
 * 
 * @param <T>
 *            the type of Entry
 */
public final class Entry<T, S extends Geometry> implements HasGeometry {
    private final T value;
    private final S geometry;

    /**
     * Constructor.
     * 
     * @param value
     *            the value of the entry
     * @param geometry
     *            the geometry of the value
     */
    public Entry(T value, S geometry) {
        Preconditions.checkNotNull(geometry);
        this.value = value;
        this.geometry = geometry;
    }

    /**
     * Factory method.
     * 
     * @param <T>
     *            type of value
     * @param <S>
     *            type of geometry
     * @param value
     *            object being given a spatial context
     * @param geometry
     *            geometry associated with the value
     * @return entry wrapping value and associated geometry
     */
    public static <T, S extends Geometry> Entry<T, S> entry(T value, S geometry) {
        return new Entry<T, S>(value, geometry);
    }

    /**
     * Returns the value wrapped by this {@link Entry}.
     * 
     * @return the entry value
     */
    public T value() {
        return value;
    }

    @Override
    public S geometry() {
        return geometry;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Entry [value=");
        builder.append(value);
        builder.append(", geometry=");
        builder.append(geometry);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value, geometry);
    }

    @Override
    public boolean equals(Object obj) {
        @SuppressWarnings("rawtypes")
        Optional<Entry> other = ObjectsHelper.asClass(obj, Entry.class);
        if (other.isPresent()) {
            return Objects.equal(value, other.get().value)
                    && Objects.equal(geometry, other.get().geometry);
        } else
            return false;
    }

}
