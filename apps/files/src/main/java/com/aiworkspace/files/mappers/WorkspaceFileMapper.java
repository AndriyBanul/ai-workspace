package com.aiworkspace.files.mappers;

import com.aiworkspace.files.models.WorkspaceFile;
import com.aiworkspace.files.repositories.WorkspaceFileEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceFileMapper {

    WorkspaceFile toModel(WorkspaceFileEntity entity);
}
