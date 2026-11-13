package nu.fgv.spexipedia.server.image;

import nu.fgv.spexipedia.server.util.Ids;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Captions and the trash of images.
 *
 * @author Anders Jacobsson
 * @since 1.0
 */
@Controller
public class ImageGraphqlApi {

    private final ImageService service;

    public ImageGraphqlApi(final ImageService service) {
        this.service = service;
    }

    @MutationMapping
    public ImageDto imageUpdate(@Argument final String id, @Argument final Map<String, String> input) {
        return service.update(Ids.require(id), input.get("caption"), input.get("alt"));
    }

    @MutationMapping
    public boolean imageTrash(@Argument final String id) {
        return service.trash(Ids.require(id));
    }

    @MutationMapping
    public ImageDto imageRestore(@Argument final String id) {
        return service.restore(Ids.require(id));
    }
}
