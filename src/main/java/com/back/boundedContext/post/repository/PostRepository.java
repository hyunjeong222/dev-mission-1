package com.back.boundedContext.post.repository;

import com.back.boundedContext.post.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Integer> {
}
