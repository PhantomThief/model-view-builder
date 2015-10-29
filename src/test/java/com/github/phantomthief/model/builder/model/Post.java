/**
 * 
 */
package com.github.phantomthief.model.builder.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.phantomthief.model.builder.util.ToStringUtils;

/**
 * @author w.vela
 */
public class Post implements HasUser, HasId<Long> {

    private final long id;
    private final int userId;
    private final List<Long> commentIds;
    @JsonIgnore
    private List<Comment> comments;

    /**
     * @param id
     * @param userId
     * @param comments
     */
    public Post(long id, int userId, List<Long> commentIds) {
        this.id = id;
        this.userId = userId;
        this.commentIds = commentIds;
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.model.HasId#getId()
     */
    @Override
    public Long getId() {
        return id;
    }

    /* (non-Javadoc)
     * @see com.github.phantomthief.model.builder.model.HasUser#getUserId()
     */
    @Override
    public Integer getUserId() {
        return userId;
    }

    public List<Comment> comments() {
        return comments;
    }

    public List<Long> getCommentIds() {
        return commentIds;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

    @Override
    public String toString() {
        return ToStringUtils.toString(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (id ^ (id >>> 32));
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
        if (!(obj instanceof Post)) {
            return false;
        }
        Post other = (Post) obj;
        if (id != other.id) {
            return false;
        }
        return true;
    }
}
