import com.github.plokhotnyuk.jsoniter_scala.circe.JsoniterScalaCodec._
import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros.{
  ConfiguredJsonValueCodec,
  JsonCodecMaker
}
import io.circe.generic.auto._
import cats.parse.strings.Json

given JsonValueCodec[List[Permission]] = JsonCodecMaker.make

enum ResourceType derives ConfiguredJsonValueCodec {
  case USER
  case ROLE
  case OTHER
  case UNIVERSE
}

enum Action derives ConfiguredJsonValueCodec {
  case CREATE
  case READ
  case UPDATE_ROLE_BINDINGS
  case UPDATE_PROFILE
  case DELETE
  case UPDATE
  case DEBUG
  case PAUSE_RESUME
  case BACKUP_RESTORE
  case XCLUSTER
  case SUPER_ADMIN_ACTIONS
}

final case class PrerequisitePermission(
    resourceType: ResourceType,
    action: Action
) derives ConfiguredJsonValueCodec

final case class Permission(
    resourceType: ResourceType,
    action: Action,
    name: String,
    description: String,
    permissionValidOnResource: Boolean,
    prerequisitePermissions: Set[PrerequisitePermission]
) derives ConfiguredJsonValueCodec

object Permission {
  val ViewProfile = Permission(
    resourceType = ResourceType.USER,
    action = Action.READ,
    name = "View Profile",
    description = "Allows viewing of user profile information.",
    permissionValidOnResource = true,
    prerequisitePermissions = Set.empty
  )

  val UpdateProfile = Permission(
    resourceType = ResourceType.USER,
    action = Action.UPDATE_PROFILE,
    name = "Update Profile",
    description = "Allows updating of user profile information.",
    permissionValidOnResource = true,
    prerequisitePermissions = Set(
      PrerequisitePermission(ResourceType.USER, Action.READ)
    )
  ) 

  val userResourcePermissions = """[
  {
    "resourceType": "USER",
    "action": "CREATE",
    "name": "Create User",
    "description": "Allows user to create a user.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "USER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "USER",
    "action": "READ",
    "name": "View User",
    "description": "Allows user to read/view user.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": []
  },
  {
    "resourceType": "USER",
    "action": "UPDATE_ROLE_BINDINGS",
    "name": "Update Role Bindings",
    "description": "Allows user to update role bindings on a user.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "USER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "USER",
    "action": "UPDATE_PROFILE",
    "name": "Update User Profile",
    "description": "Allows user to update user profile.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "USER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "USER",
    "action": "DELETE",
    "name": "Delete User",
    "description": "Allows user to delete user.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "USER",
        "action": "READ"
      }
    ]
  }
]""".stripMargin

  val permissions: List[Permission] =
    readFromString[List[Permission]](userResourcePermissions)

  val allPermissions: List[Permission] = List(
    Permission(
      resourceType = ResourceType.USER,
      action = Action.CREATE,
      name = "Create User",
      description = "Allows user to create a user.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.USER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.USER,
      action = Action.READ,
      name = "View User",
      description = "Allows user to read/view user.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set.empty
    ),
    Permission(
      resourceType = ResourceType.USER,
      action = Action.UPDATE_ROLE_BINDINGS,
      name = "Update Role Bindings",
      description = "Allows user to update role bindings on a user.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.USER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.USER,
      action = Action.UPDATE_PROFILE,
      name = "Update User Profile",
      description = "Allows user to update user profile.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.USER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.USER,
      action = Action.DELETE,
      name = "Delete User",
      description = "Allows user to delete user.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.USER, Action.READ)
      )
    )
  )

  val otherResourcePermissions = """[
  {
    "resourceType": "OTHER",
    "action": "CREATE",
    "name": "Create Resource",
    "description": "Permission to create a resource instance.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "OTHER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "OTHER",
    "action": "READ",
    "name": "View Resource",
    "description": "Permission to read a resource instance.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": []
  },
  {
    "resourceType": "OTHER",
    "action": "UPDATE",
    "name": "Update Resource",
    "description": "Permission to update a resource instance.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "OTHER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "OTHER",
    "action": "DELETE",
    "name": "Delete Resource",
    "description": "Permission to delete a resource instance.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "OTHER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "OTHER",
    "action": "SUPER_ADMIN_ACTIONS",
    "name": "Super Admin Actions",
    "description": "Gives all the super admin permissions like HA, runtime configs, etc.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": []
  }
]""".stripMargin

  val otherPermissions: List[Permission] =
    readFromString[List[Permission]](otherResourcePermissions)

  val allOtherPermissions: List[Permission] = List(
    Permission(
      resourceType = ResourceType.OTHER,
      action = Action.CREATE,
      name = "Create Resource",
      description = "Permission to create a resource instance.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.OTHER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.OTHER,
      action = Action.READ,
      name = "View Resource",
      description = "Permission to read a resource instance.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set.empty
    ),
    Permission(
      resourceType = ResourceType.OTHER,
      action = Action.UPDATE,
      name = "Update Resource",
      description = "Permission to update a resource instance.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.OTHER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.OTHER,
      action = Action.DELETE,
      name = "Delete Resource",
      description = "Permission to delete a resource instance.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.OTHER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.OTHER,
      action = Action.SUPER_ADMIN_ACTIONS,
      name = "Super Admin Actions",
      description =
        "Gives all the super admin permissions like HA, runtime configs, etc.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set.empty
    )
  )

  val roleResourcePermissions = """[
  {
    "resourceType": "ROLE",
    "action": "CREATE",
    "name": "Create Role",
    "description": "Allows user to create a custom role.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "ROLE",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "ROLE",
    "action": "READ",
    "name": "View Role",
    "description": "Allows user to read/view a role.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": []
  },
  {
    "resourceType": "ROLE",
    "action": "UPDATE",
    "name": "Update Role",
    "description": "Allows user to update a role.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "ROLE",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "ROLE",
    "action": "DELETE",
    "name": "Delete Role",
    "description": "Allows user to delete a role.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "ROLE",
        "action": "READ"
      }
    ]
  }
]""".stripMargin

  val rolePermissions: List[Permission] =
    readFromString[List[Permission]](roleResourcePermissions)

  val allRolePermissions: List[Permission] = List(
    Permission(
      resourceType = ResourceType.ROLE,
      action = Action.CREATE,
      name = "Create Role",
      description = "Allows user to create a custom role.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.ROLE, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.ROLE,
      action = Action.READ,
      name = "View Role",
      description = "Allows user to read/view a role.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set.empty  
    ),
    Permission(
      resourceType = ResourceType.ROLE,
      action = Action.UPDATE,
      name = "Update Role",
      description = "Allows user to update a role.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.ROLE, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.ROLE,
      action = Action.DELETE,
      name = "Delete Role",
      description = "Allows user to delete a role.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.ROLE, Action.READ)
      )
    )
  )

  val universResourcePermissions = """[
  {
    "resourceType": "UNIVERSE",
    "action": "CREATE",
    "name": "Create Universe",
    "description": "Allows user to create a universe.",
    "permissionValidOnResource": false,
    "prerequisitePermissions": [
      {
        "resourceType": "UNIVERSE",
        "action": "READ"
      },
      {
        "resourceType": "OTHER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "UNIVERSE",
    "action": "READ",
    "name": "View Universe",
    "description": "Allows user to view a universe.",
    "permissionValidOnResource": true,
    "prerequisitePermissions": [
      {
        "resourceType": "OTHER",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "UNIVERSE",
    "action": "DEBUG",
    "name": "Debug Universe",
    "description": "Allows user to Debug a universe. User can create support bundles, reset slow queries, run perf advisor, etc.",
    "permissionValidOnResource": true,
    "prerequisitePermissions": [
      {
        "resourceType": "UNIVERSE",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "UNIVERSE",
    "action": "UPDATE",
    "name": "Update Universe",
    "description": "Allows user to update a universe.",
    "permissionValidOnResource": true,
    "prerequisitePermissions": [
      {
        "resourceType": "UNIVERSE",
        "action": "READ"
      },
      {
        "resourceType": "UNIVERSE",
        "action": "DEBUG"
      }
    ]
  },
  {
    "resourceType": "UNIVERSE",
    "action": "DELETE",
    "name": "Delete Universe",
    "description": "Allows user to DELETE a universe.",
    "permissionValidOnResource": true,
    "prerequisitePermissions": [
      {
        "resourceType": "UNIVERSE",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "UNIVERSE",
    "action": "PAUSE_RESUME",
    "name": "Pause/Resume Universe",
    "description": "Allows user to pause and resume a universe.",
    "permissionValidOnResource": true,
    "prerequisitePermissions": [
      {
        "resourceType": "UNIVERSE",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "UNIVERSE",
    "action": "BACKUP_RESTORE",
    "name": "Backup/Restore Universe",
    "description": "Allows user to create a backup on a universe.",
    "permissionValidOnResource": true,
    "prerequisitePermissions": [
      {
        "resourceType": "UNIVERSE",
        "action": "READ"
      }
    ]
  },
  {
    "resourceType": "UNIVERSE",
    "action": "XCLUSTER",
    "name": "Manage xCluster",
    "description": "Manges xCluster on a universe.",
    "permissionValidOnResource": true,
    "prerequisitePermissions": [
      {
        "resourceType": "UNIVERSE",
        "action": "READ"
      }
    ]
  }
]""".stripMargin

  val universePermissions: List[Permission] =
    readFromString[List[Permission]](universResourcePermissions)

  val allUniversePermissions: List[Permission] = List(
    Permission(
      resourceType = ResourceType.UNIVERSE,
      action = Action.CREATE,
      name = "Create Universe",
      description = "Allows user to create a universe.",
      permissionValidOnResource = false,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.UNIVERSE, Action.READ),
        PrerequisitePermission(ResourceType.OTHER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.UNIVERSE,
      action = Action.READ,
      name = "View Universe",
      description = "Allows user to view a universe.",
      permissionValidOnResource = true,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.OTHER, Action.READ)
      )
    ),
    Permission(
      resourceType = ResourceType.UNIVERSE,
      action = Action.DEBUG,
      name = "Debug Universe",
      description =
        "Allows user to Debug a universe. User can create support bundles, reset slow queries, run perf advisor, etc.",
      permissionValidOnResource = true,
      prerequisitePermissions = Set(
        PrerequisitePermission(ResourceType.UNIVERSE, Action.READ)
      )
    )
  
  )
}
