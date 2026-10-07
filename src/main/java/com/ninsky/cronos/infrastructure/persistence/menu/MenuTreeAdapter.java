package com.ninsky.cronos.infrastructure.persistence.menu;

import com.ninsky.cronos.domain.model.menu.MenuNode;
import com.ninsky.cronos.domain.port.menu.MenuPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Infrastructure adapter for {@link MenuPort}. The flat row set is fetched via {@link MenuCustomRepository}
 * (a separate bean so its {@code @Cacheable} method goes through the Spring proxy — self-invocation
 * within one class would otherwise bypass caching entirely). Tree assembly clones each cached node
 * before mutating it, since {@code @Cacheable} returns the same shared instances on every hit and
 * this adapter builds a different pruned tree per caller's permission set.
 */
@Component
public class MenuTreeAdapter implements MenuPort {

    private final MenuCustomRepository menuItemLoader;

    public MenuTreeAdapter(MenuCustomRepository menuItemLoader) {
        this.menuItemLoader = menuItemLoader;
    }

    @Override
    public List<MenuNode> buildTree(Set<String> grantedPermissionNames) {
        List<MenuNode> allItems = menuItemLoader.fetchAllActive().stream()
                .map(MenuTreeAdapter::copyOf)
                .collect(Collectors.toList());
        List<MenuNode> roots = assembleTree(allItems);
        return roots.stream()
                .map(node -> prune(node, grantedPermissionNames))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static MenuNode copyOf(MenuNode source) {
        return MenuNode.builder()
                .id(source.getId())
                .parentId(source.getParentId())
                .code(source.getCode())
                .labelEn(source.getLabelEn())
                .labelEs(source.getLabelEs())
                .icon(source.getIcon())
                .path(source.getPath())
                .displayOrder(source.getDisplayOrder())
                .requiredPermission(source.getRequiredPermission())
                .build();
    }

    private List<MenuNode> assembleTree(List<MenuNode> flat) {
        Map<Long, MenuNode> byId = new LinkedHashMap<>();
        flat.forEach(node -> byId.put(node.getId(), node));

        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : flat) {
            if (node.getParentId() == null) {
                roots.add(node);
            } else {
                MenuNode parent = byId.get(node.getParentId());
                if (parent != null) {
                    parent.getChildren().add(node);
                }
            }
        }
        roots.sort(Comparator.comparingInt(MenuNode::getDisplayOrder));
        byId.values().forEach(n -> n.getChildren().sort(Comparator.comparingInt(MenuNode::getDisplayOrder)));
        return roots;
    }

    /** Keeps a node if its own permission is granted; a permission-less "category" node is kept only if it still has visible children. */
    private MenuNode prune(MenuNode node, Set<String> grantedPermissionNames) {
        List<MenuNode> prunedChildren = node.getChildren().stream()
                .map(child -> prune(child, grantedPermissionNames))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        boolean selfGranted = node.getRequiredPermission() == null || grantedPermissionNames.contains(node.getRequiredPermission());
        if (!selfGranted) {
            return null;
        }
        if (node.getPath() == null && prunedChildren.isEmpty()) {
            return null;
        }

        node.setChildren(prunedChildren);
        return node;
    }
}
