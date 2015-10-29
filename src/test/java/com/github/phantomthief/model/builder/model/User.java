/**
 * 
 */
package com.github.phantomthief.model.builder.model;

import com.github.phantomthief.model.builder.util.ToStringUtils;

/**
 * @author w.vela
 */
public class User implements HasId<Integer> {

    private final int id;

    /**
     * @param id
     */
    public User(int id) {
        this.id = id;
    }

    @Override
    public Integer getId() {
        return id;
    }

    @Override
    public String toString() {
        return ToStringUtils.toString(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof User)) {
            return false;
        }
        User other = (User) obj;
        if (id != other.id) {
            return false;
        }
        return true;
    }
}
