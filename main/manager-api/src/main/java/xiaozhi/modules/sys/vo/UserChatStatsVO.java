package xiaozhi.modules.sys.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * User chat statistics view object
 *
 * @author Admin
 * @since 2025-09-06
 */
@Data
@Schema(description = "User chat statistics for different periods")
public class UserChatStatsVO {
    
    @Schema(description = "User ID")
    private Long userId;
    
    @Schema(description = "Last 3 months chat count")
    private Integer last3MonthsCount;
    
    @Schema(description = "Current month chat count")
    private Integer currentMonthCount;
}
