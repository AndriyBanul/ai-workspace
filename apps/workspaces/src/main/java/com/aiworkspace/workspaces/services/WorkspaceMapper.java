package com.aiworkspace.workspaces.services;

import com.aiworkspace.workspaces.models.Workspace;
import com.aiworkspace.workspaces.repositories.WorkspaceEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
interface WorkspaceMapper {

    Workspace toModel(WorkspaceEntity entity);
}
