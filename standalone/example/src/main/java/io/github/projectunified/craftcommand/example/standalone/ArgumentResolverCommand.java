package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Resolve;

/**
 * Command to test various ArgumentResolver features in standalone commands:
 * - Single-width resolvers
 * - Multi-width resolvers (2D, 3D vectors)
 * - Zero-width contextual resolvers
 * - Hierarchy / interface-based resolvers
 * - Optional parameters with resolvers and default values
 * - Combinations of multiple custom resolvers
 * - Error throwing inside resolvers
 */
@Command(value = "resolvertest", description = "Test ArgumentResolver cases")
public class ArgumentResolverCommand {

    @Command("user")
    public void user(Object sender, TargetUser user) {
        ((TestSender) sender).sendMessage("user=" + user.name);
    }

    @Command("vec2")
    public void vec2(Object sender, Vec2D vec) {
        ((TestSender) sender).sendMessage("vec2=" + vec.x + "," + vec.y);
    }

    @Command("vec3")
    public void vec3(Object sender, Vec3D vec) {
        ((TestSender) sender).sendMessage("vec3=" + vec.x + "," + vec.y + "," + vec.z);
    }

    @Command("session")
    public void session(@Resolve SessionToken token, String action) {
        ((TestSender) token.originalSender).sendMessage("session=" + token.token + ",action=" + action);
    }

    @Command("tag")
    public void tag(Object sender, CustomTag tag) {
        ((TestSender) sender).sendMessage("tag=" + tag.tagName);
    }

    @Command("optvec")
    public void optVec(Object sender, @Default("10 20") Vec2D vec) {
        ((TestSender) sender).sendMessage("optvec=" + vec.x + "," + vec.y);
    }

    @Command("optuser")
    public void optUser(Object sender, @Default("guest") TargetUser user) {
        ((TestSender) sender).sendMessage("optuser=" + (user != null ? user.name : "null"));
    }

    @Command("combo")
    public void combo(Object sender, TargetUser user, Vec2D origin, Vec3D target, @Default("5") int speed) {
        ((TestSender) sender).sendMessage(String.format("combo:user=%s,origin=%d,%d,target=%d,%d,%d,speed=%d",
                user.name, origin.x, origin.y, target.x, target.y, target.z, speed));
    }

    @Command("port")
    public void port(Object sender, StrictPort port) {
        ((TestSender) sender).sendMessage("port=" + port.port);
    }

    public interface BaseTag {
        String getTag();
    }

    public static class TargetUser {
        public final String name;

        public TargetUser(String name) {
            this.name = name;
        }
    }

    public static class Vec2D {
        public final int x;
        public final int y;

        public Vec2D(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public static class Vec3D {
        public final int x;
        public final int y;
        public final int z;

        public Vec3D(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    public static class SessionToken {
        public final Object originalSender;
        public final String token;

        public SessionToken(Object originalSender, String token) {
            this.originalSender = originalSender;
            this.token = token;
        }
    }

    public static class CustomTag implements BaseTag {
        public final String tagName;

        public CustomTag(String tagName) {
            this.tagName = tagName;
        }

        @Override
        public String getTag() {
            return tagName;
        }
    }

    public static class StrictPort {
        public final int port;

        public StrictPort(int port) {
            this.port = port;
        }
    }
}
