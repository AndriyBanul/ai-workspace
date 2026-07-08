package com.aiworkspace.workspaces.mappers;

import com.aiworkspace.workspaces.entities.WorkspaceFileEntity;
import com.aiworkspace.workspaces.models.WorkspaceFile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WorkspaceFileMapper {

    WorkspaceFile toModel(WorkspaceFileEntity entity);
}
