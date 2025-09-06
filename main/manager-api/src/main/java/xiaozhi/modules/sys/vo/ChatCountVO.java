package xiaozhi.modules.sys.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Chat count statistics view object
 *
 * @author Admin
 * @since 2025-09-03
 */
@Data
@Schema(description = "Chat count statistics")
public class ChatCountVO {
    
    @Schema(description = "User ID")
    private Long userId;
    
    @Schema(description = "Username")
    private String username;
    
    @Schema(description = "Chat count")
    private Integer chatCount;
}
