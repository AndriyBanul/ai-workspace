package com.aiworkspace.workspaces.mappers;

import com.aiworkspace.workspaces.models.WorkspaceFile;
import com.aiworkspace.workspaces.repositories.WorkspaceFileEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceFileMapper {

    WorkspaceFile toModel(WorkspaceFileEntity entity);
}
